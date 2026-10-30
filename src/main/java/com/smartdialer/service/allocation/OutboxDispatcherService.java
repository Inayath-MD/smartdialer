package com.smartdialer.service.allocation;

import com.smartdialer.entity.CallRecord;
import com.smartdialer.entity.OutboxEvent;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.enums.OutboxStatus;
import com.smartdialer.provider.TelecomProvider;
import com.smartdialer.provider.TelecomProviderRegistry;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.repository.OutboxEventRepository;
import com.smartdialer.service.agent.AgentService;
import com.smartdialer.service.borrower.BorrowerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OutboxDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcherService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final CallRecordRepository callRecordRepository;
    private final TelecomProviderRegistry telecomProviderRegistry;
    private final AgentService agentService;
    private final BorrowerService borrowerService;

    public OutboxDispatcherService(
            OutboxEventRepository outboxEventRepository,
            CallRecordRepository callRecordRepository,
            TelecomProviderRegistry telecomProviderRegistry,
            AgentService agentService,
            BorrowerService borrowerService
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.callRecordRepository = callRecordRepository;
        this.telecomProviderRegistry = telecomProviderRegistry;
        this.agentService = agentService;
        this.borrowerService = borrowerService;
    }

    /**
     * Polls and processes pending outbox dispatch events.
     * The network call to TelecomProvider is performed strictly outside the database transaction.
     */
    public int processPendingDispatches() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        int processedCount = 0;

        for (OutboxEvent event : pending) {
            try {
                processSingleDispatch(event);
                processedCount++;
            } catch (Exception e) {
                log.error("Error processing outbox event id={}: {}", event.getId(), e.getMessage(), e);
            }
        }
        return processedCount;
    }

    public void processSingleDispatch(OutboxEvent event) {
        // Step 1: Mark outbox event as PROCESSING
        markProcessing(event.getId());

        Long callId = Long.parseLong(event.getAggregateId());
        CallRecord call = callRecordRepository.findById(callId).orElse(null);

        if (call == null) {
            log.error("Call id={} not found for outbox event id={}", callId, event.getId());
            markFailed(event.getId(), "Call not found");
            return;
        }

        // Idempotency check: If call is already initiated or past initiation, complete outbox immediately
        if (call.getStatus() != CallStatus.RESERVED) {
            log.info("Call id={} already in state {}. Marking outbox event as completed.", callId, call.getStatus());
            markCompleted(event.getId());
            return;
        }

        // Parse payload values
        String phoneNumber = extractString(event.getPayload(), "phoneNumber");
        String idempotencyKey = call.getIdempotencyKey();

        // Step 2: Make telecom network call OUTSIDE of any database transaction!
        TelecomProvider provider = telecomProviderRegistry.getActiveProvider();
        TelecomProvider.InitiateCallRequest request = new TelecomProvider.InitiateCallRequest(
                call.getId(),
                call.getCampaignId(),
                phoneNumber,
                idempotencyKey
        );

        TelecomProvider.InitiateCallResponse response = provider.initiateCall(request);

        // Step 3: Record outcome in separate database transaction
        recordDispatchOutcome(event.getId(), callId, provider.getProviderType().name(), response);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long outboxId) {
        outboxEventRepository.findById(outboxId).ifPresent(event -> {
            event.setStatus(OutboxStatus.PROCESSING);
            outboxEventRepository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long outboxId) {
        outboxEventRepository.findById(outboxId).ifPresent(event -> {
            event.setStatus(OutboxStatus.COMPLETED);
            event.setProcessedAt(Instant.now());
            outboxEventRepository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long outboxId, String reason) {
        outboxEventRepository.findById(outboxId).ifPresent(event -> {
            event.setStatus(OutboxStatus.FAILED);
            event.setProcessedAt(Instant.now());
            event.setErrorMessage(reason);
            outboxEventRepository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDispatchOutcome(
            Long outboxId,
            Long callId,
            String providerName,
            TelecomProvider.InitiateCallResponse response
    ) {
        CallRecord call = callRecordRepository.findById(callId).orElse(null);
        if (call == null) return;

        if (response.success()) {
            call.setProviderCallId(response.providerCallId());
            call.setProviderName(providerName);
            call.setStatus(CallStatus.INITIATED);
            call.setInitiatedAt(Instant.now());
            callRecordRepository.save(call);

            // Update borrower to DIALING
            borrowerService.markDialing(call.getBorrowerId());

            // If agent pre-reserved, update agent to DIALING
            if (call.getAgentId() != null) {
                agentService.markDialing(call.getAgentId(), call.getId());
            }

            markCompleted(outboxId);
            log.info("Dispatched call id={} with providerCallId={}", callId, response.providerCallId());
        } else {
            // Telecom initiation failed
            call.setStatus(CallStatus.FAILED);
            call.setFailureReason("INITIATION_FAILED: " + response.errorCode());
            call.setCompletedAt(Instant.now());
            callRecordRepository.save(call);

            // Release agent if reserved
            if (call.getAgentId() != null) {
                agentService.releaseAgent(call.getAgentId());
            }

            // Mark borrower failed / cooldown (standard 120s cooldown, 3 max attempts)
            borrowerService.markFailed(call.getBorrowerId(), 120, 3);

            markFailed(outboxId, response.errorMessage());
            log.warn("Call id={} telecom initiation failed: {}", callId, response.errorMessage());
        }
    }

    private String extractString(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
}
