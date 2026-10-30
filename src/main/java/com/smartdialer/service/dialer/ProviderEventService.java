package com.smartdialer.service.dialer;

import com.smartdialer.dto.ProviderCallbackEvent;
import com.smartdialer.entity.Agent;
import com.smartdialer.entity.CallRecord;
import com.smartdialer.entity.ProviderEventRecord;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.enums.ProcessingStatus;
import com.smartdialer.enums.ProviderEventType;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.repository.ProviderEventRecordRepository;
import com.smartdialer.service.agent.AgentService;
import com.smartdialer.service.borrower.BorrowerService;
import com.smartdialer.service.pacing.PredictivePacingService;
import com.smartdialer.state.CallStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provider Event Service.
 * 
 * Handles incoming telecom provider webhooks transactionally, idempotently,
 * and enforces strict call state machine invariants, monotonic progression,
 * and the zero-orphan invariant:
 * NO CONNECTED BORROWER CALL MAY EXIST WITHOUT AN ASSIGNED ELIGIBLE AGENT.
 */
@Service
public class ProviderEventService {

    private static final Logger log = LoggerFactory.getLogger(ProviderEventService.class);

    private final ProviderEventRecordRepository providerEventRecordRepository;
    private final CallRecordRepository callRecordRepository;
    private final CallStateMachine callStateMachine;
    private final AgentService agentService;
    private final BorrowerService borrowerService;
    private final PredictivePacingService predictivePacingService;

    // Safety incident counter
    private final AtomicInteger abandonedCallCounter = new AtomicInteger(0);

    public ProviderEventService(
            ProviderEventRecordRepository providerEventRecordRepository,
            CallRecordRepository callRecordRepository,
            CallStateMachine callStateMachine,
            AgentService agentService,
            BorrowerService borrowerService,
            PredictivePacingService predictivePacingService
    ) {
        this.providerEventRecordRepository = providerEventRecordRepository;
        this.callRecordRepository = callRecordRepository;
        this.callStateMachine = callStateMachine;
        this.agentService = agentService;
        this.borrowerService = borrowerService;
        this.predictivePacingService = predictivePacingService;
    }

    @Transactional
    public ProcessingStatus processProviderEvent(ProviderCallbackEvent event) {
        // Step 1: Deduplication by unique providerEventId
        if (providerEventRecordRepository.existsByProviderEventId(event.providerEventId())) {
            log.info("Idempotent ignore: Provider event {} already processed.", event.providerEventId());
            return ProcessingStatus.DUPLICATE;
        }

        // Step 2: Store provider event durably
        ProviderEventRecord eventRecord = new ProviderEventRecord(
                event.providerEventId(),
                event.providerCallId(),
                event.eventType(),
                event.toString()
        );
        providerEventRecordRepository.save(eventRecord);

        // Step 3: Find associated CallRecord
        CallRecord call = callRecordRepository.findByProviderCallId(event.providerCallId()).orElse(null);
        if (call == null) {
            log.warn("Call not found for providerCallId={}. Event recorded as IGNORED.", event.providerCallId());
            eventRecord.setProcessingStatus(ProcessingStatus.IGNORED);
            eventRecord.setProcessedAt(Instant.now());
            providerEventRecordRepository.save(eventRecord);
            return ProcessingStatus.IGNORED;
        }

        CallStatus targetCallStatus = mapEventToCallStatus(event.eventType());

        // Step 4: Protect against terminal state regression and out-of-order events
        if (callStateMachine.isStaleOrDuplicate(call.getStatus(), targetCallStatus)) {
            log.info("Ignoring out-of-order/stale/duplicate event: currentStatus={}, incomingStatus={}, eventId={}",
                    call.getStatus(), targetCallStatus, event.providerEventId());
            eventRecord.setProcessingStatus(ProcessingStatus.PROCESSED);
            eventRecord.setProcessedAt(Instant.now());
            providerEventRecordRepository.save(eventRecord);
            return ProcessingStatus.PROCESSED;
        }

        // Validate state machine transition
        callStateMachine.validateTransition(call.getStatus(), targetCallStatus);

        // Step 5: Process according to event type
        switch (event.eventType()) {
            case RINGING -> handleRinging(call);
            case ANSWERED -> handleAnswered(call);
            case CONNECTED -> handleConnected(call);
            case COMPLETED -> handleCompleted(call);
            case FAILED -> handleFailed(call, event.failureReason());
            default -> log.debug("Unhandled provider event type: {}", event.eventType());
        }

        eventRecord.setProcessingStatus(ProcessingStatus.PROCESSED);
        eventRecord.setProcessedAt(Instant.now());
        providerEventRecordRepository.save(eventRecord);

        return ProcessingStatus.PROCESSED;
    }

    private void handleRinging(CallRecord call) {
        call.setStatus(CallStatus.RINGING);
        callRecordRepository.save(call);
        log.debug("Call id={} providerCallId={} is RINGING", call.getId(), call.getProviderCallId());
    }

    /**
     * Handles borrower answer.
     * CRITICAL SAFETY INVARIANT:
     * Never mark CONNECTED unless an eligible agent is confirmed assigned.
     */
    private void handleAnswered(CallRecord call) {
        call.setAnsweredAt(Instant.now());
        call.setStatus(CallStatus.ANSWERED);

        Long agentId = call.getAgentId();
        boolean agentAvailableAndAssigned = false;

        // Check if pre-reserved agent is still eligible and available
        if (agentId != null) {
            Agent agent = agentService.findEntityById(agentId);
            if (agent.getStatus() == AgentStatus.RESERVED || agent.getStatus() == AgentStatus.DIALING) {
                agentAvailableAndAssigned = true;
            } else {
                log.warn("Assigned agent id={} was in status {} when call answered. Looking for replacement.",
                        agentId, agent.getStatus());
            }
        }

        // If no pre-reserved agent or pre-reserved agent became unavailable (predictive dialing), try to dynamically assign an available agent
        if (!agentAvailableAndAssigned) {
            List<Agent> availableAgents = agentService.getAvailableAgentsForCampaign(call.getCampaignId(), 1);
            if (!availableAgents.isEmpty()) {
                Agent replacement = availableAgents.get(0);
                boolean reserved = agentService.reserveAgent(replacement.getId(), call.getId());
                if (reserved) {
                    agentId = replacement.getId();
                    call.setAgentId(agentId);
                    agentAvailableAndAssigned = true;
                    log.info("Dynamically assigned available agent id={} to answered call id={}", agentId, call.getId());
                }
            }
        }

        if (agentAvailableAndAssigned) {
            // SUCCESSFUL BRIDGE: Mark CONNECTED
            call.setStatus(CallStatus.CONNECTED);
            callRecordRepository.save(call);

            agentService.markConnected(agentId, call.getId());
            borrowerService.markConnected(call.getBorrowerId());

            log.info("Call id={} successfully BRIDGED and CONNECTED to Agent id={}", call.getId(), agentId);
        } else {
            // CRITICAL SAFETY INVARIANT ENFORCEMENT:
            // Borrower answered, but NO eligible agent is available!
            // Abandon immediately, DO NOT MARK CONNECTED.
            abandonedCallCounter.incrementAndGet();
            call.setStatus(CallStatus.CANCELLED);
            call.setFailureReason("SAFETY_ABANDONED_NO_ELIGIBLE_AGENT_AVAILABLE");
            call.setCompletedAt(Instant.now());
            callRecordRepository.save(call);

            borrowerService.markFailed(call.getBorrowerId(), 300, 3); // 5 min cooldown

            log.error("[SAFETY INCIDENT] Call id={} borrower answered, but NO eligible agent available. Call abandoned safely. Total abandoned: {}",
                    call.getId(), abandonedCallCounter.get());
        }
    }

    private void handleConnected(CallRecord call) {
        if (call.getStatus() != CallStatus.CONNECTED) {
            if (call.getAgentId() != null) {
                call.setStatus(CallStatus.CONNECTED);
                callRecordRepository.save(call);
                agentService.markConnected(call.getAgentId(), call.getId());
            } else {
                handleAnswered(call);
            }
        }
    }

    private void handleCompleted(CallRecord call) {
        call.setStatus(CallStatus.COMPLETED);
        call.setCompletedAt(Instant.now());
        callRecordRepository.save(call);

        long durationSeconds = 0;
        if (call.getAnsweredAt() != null) {
            durationSeconds = Duration.between(call.getAnsweredAt(), call.getCompletedAt()).toSeconds();
        }

        // Release Agent
        if (call.getAgentId() != null) {
            agentService.markWrapUp(call.getAgentId());
        }

        // Complete Borrower
        borrowerService.markCompleted(call.getBorrowerId());

        // Update predictive pacing statistical history
        predictivePacingService.recordCallOutcome(call.getCampaignId(), true, durationSeconds);

        log.info("Call id={} completed. Duration={}s", call.getId(), durationSeconds);
    }

    private void handleFailed(CallRecord call, String reason) {
        call.setStatus(CallStatus.FAILED);
        call.setFailureReason(reason != null ? reason : "PROVIDER_REPORTED_FAILURE");
        call.setCompletedAt(Instant.now());
        callRecordRepository.save(call);

        // Release Agent if reserved
        if (call.getAgentId() != null) {
            agentService.releaseAgent(call.getAgentId());
        }

        // Fail borrower attempt
        borrowerService.markFailed(call.getBorrowerId(), 120, 3);

        // Update pacing engine
        predictivePacingService.recordCallOutcome(call.getCampaignId(), false, 0);

        log.info("Call id={} failed. Reason: {}", call.getId(), call.getFailureReason());
    }

    private CallStatus mapEventToCallStatus(ProviderEventType eventType) {
        return switch (eventType) {
            case INITIATED -> CallStatus.INITIATED;
            case RINGING -> CallStatus.RINGING;
            case ANSWERED -> CallStatus.ANSWERED;
            case CONNECTED -> CallStatus.CONNECTED;
            case COMPLETED -> CallStatus.COMPLETED;
            case FAILED -> CallStatus.FAILED;
        };
    }

    public int getAbandonedCallCount() {
        return abandonedCallCounter.get();
    }
}
