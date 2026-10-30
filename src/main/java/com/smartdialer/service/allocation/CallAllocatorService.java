package com.smartdialer.service.allocation;

import com.smartdialer.entity.Agent;
import com.smartdialer.entity.Borrower;
import com.smartdialer.entity.CallRecord;
import com.smartdialer.entity.Campaign;
import com.smartdialer.entity.OutboxEvent;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.enums.DialingMode;
import com.smartdialer.enums.OutboxStatus;
import com.smartdialer.exception.ResourceNotFoundException;
import com.smartdialer.exception.SafetyViolationException;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.BorrowerRepository;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.repository.CampaignRepository;
import com.smartdialer.repository.OutboxEventRepository;
import com.smartdialer.service.agent.AgentService;
import com.smartdialer.service.borrower.BorrowerService;
import com.smartdialer.service.safety.SafetyControllerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CallAllocatorService {

    private static final Logger log = LoggerFactory.getLogger(CallAllocatorService.class);

    private final CampaignRepository campaignRepository;
    private final AgentRepository agentRepository;
    private final BorrowerRepository borrowerRepository;
    private final CallRecordRepository callRecordRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AgentService agentService;
    private final BorrowerService borrowerService;
    private final SafetyControllerService safetyControllerService;

    public CallAllocatorService(
            CampaignRepository campaignRepository,
            AgentRepository agentRepository,
            BorrowerRepository borrowerRepository,
            CallRecordRepository callRecordRepository,
            OutboxEventRepository outboxEventRepository,
            AgentService agentService,
            BorrowerService borrowerService,
            SafetyControllerService safetyControllerService
    ) {
        this.campaignRepository = campaignRepository;
        this.agentRepository = agentRepository;
        this.borrowerRepository = borrowerRepository;
        this.callRecordRepository = callRecordRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.agentService = agentService;
        this.borrowerService = borrowerService;
        this.safetyControllerService = safetyControllerService;
    }

    /**
     * Transactional allocation for Progressive dialing.
     * Guaranteed 1 available agent = 1 call.
     * Transaction is committed BEFORE telecom dispatch to avoid keeping DB connection open.
     */
    @Transactional
    public List<CallRecord> allocateProgressiveCalls(Long campaignId, int approvedCallSlots) {
        if (approvedCallSlots <= 0) {
            return List.of();
        }

        // Re-enforce safety invariant at transactional boundary
        safetyControllerService.enforceTransactionalSafety(campaignId, approvedCallSlots, DialingMode.PROGRESSIVE);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        // Fetch candidate available agents
        List<Agent> availableAgents = agentService.getAvailableAgentsForCampaign(campaignId, approvedCallSlots);
        if (availableAgents.isEmpty()) {
            log.warn("No available agents found in transaction for campaign {}", campaignId);
            return List.of();
        }

        // Fetch candidate eligible borrowers
        List<Borrower> eligibleBorrowers = borrowerService.findEligibleBorrowers(
                campaignId,
                campaign.getMaxAttemptsPerBorrower(),
                approvedCallSlots
        );
        if (eligibleBorrowers.isEmpty()) {
            log.info("No eligible borrowers available for campaign {}", campaignId);
            return List.of();
        }

        int pairsToAllocate = Math.min(Math.min(approvedCallSlots, availableAgents.size()), eligibleBorrowers.size());
        List<CallRecord> allocatedCalls = new ArrayList<>();

        for (int i = 0; i < pairsToAllocate; i++) {
            Agent agent = availableAgents.get(i);
            Borrower borrower = eligibleBorrowers.get(i);

            // Generate unique idempotency key for this call attempt
            String idempotencyKey = "CALL-" + campaignId + "-" + borrower.getId() + "-" + UUID.randomUUID();

            // Create preliminary CallRecord in RESERVED state
            CallRecord call = new CallRecord(campaignId, borrower.getId(), agent.getId(), idempotencyKey);
            CallRecord savedCall = callRecordRepository.save(call);

            // Atomically reserve the agent
            boolean agentReserved = agentService.reserveAgent(agent.getId(), savedCall.getId());
            if (!agentReserved) {
                log.warn("Race condition: Agent id={} could not be reserved. Skipping.", agent.getId());
                savedCall.setStatus(CallStatus.CANCELLED);
                savedCall.setFailureReason("AGENT_UNAVAILABLE_CONCURRENCY");
                callRecordRepository.save(savedCall);
                continue;
            }

            // Atomically reserve the borrower
            boolean borrowerReserved = borrowerService.reserveBorrower(borrower.getId());
            if (!borrowerReserved) {
                log.warn("Race condition: Borrower id={} could not be reserved. Releasing agent id={}.", borrower.getId(), agent.getId());
                agentService.releaseAgent(agent.getId());
                savedCall.setStatus(CallStatus.CANCELLED);
                savedCall.setFailureReason("BORROWER_UNAVAILABLE_CONCURRENCY");
                callRecordRepository.save(savedCall);
                continue;
            }

            // Create Outbox event for reliable post-commit dispatch
            String outboxPayload = String.format(
                    "{\"callId\":%d,\"campaignId\":%d,\"borrowerId\":%d,\"agentId\":%d,\"phoneNumber\":\"%s\",\"idempotencyKey\":\"%s\"}",
                    savedCall.getId(), campaignId, borrower.getId(), agent.getId(), borrower.getPhoneNumber(), idempotencyKey
            );
            OutboxEvent outboxEvent = new OutboxEvent(String.valueOf(savedCall.getId()), "DISPATCH_CALL", outboxPayload);
            outboxEventRepository.save(outboxEvent);

            allocatedCalls.add(savedCall);
            log.info("Allocated progressive call id={} with Agent id={} and Borrower id={}",
                    savedCall.getId(), agent.getId(), borrower.getId());
        }

        return allocatedCalls;
    }

    /**
     * Transactional allocation for Predictive dialing.
     * Can overdial according to safety controller approval.
     */
    @Transactional
    public List<CallRecord> allocatePredictiveCalls(Long campaignId, int approvedCallSlots) {
        if (approvedCallSlots <= 0) {
            return List.of();
        }

        safetyControllerService.enforceTransactionalSafety(campaignId, approvedCallSlots, DialingMode.PREDICTIVE);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        List<Borrower> eligibleBorrowers = borrowerService.findEligibleBorrowers(
                campaignId,
                campaign.getMaxAttemptsPerBorrower(),
                approvedCallSlots
        );

        if (eligibleBorrowers.isEmpty()) {
            log.info("Predictive dialer: No eligible borrowers found for campaign {}", campaignId);
            return List.of();
        }

        // Available agents if any (assigned if available, else buffered for bridging on answer)
        List<Agent> availableAgents = agentService.getAvailableAgentsForCampaign(campaignId, approvedCallSlots);

        List<CallRecord> allocatedCalls = new ArrayList<>();
        int countToDial = Math.min(approvedCallSlots, eligibleBorrowers.size());

        for (int i = 0; i < countToDial; i++) {
            Borrower borrower = eligibleBorrowers.get(i);
            Long assignedAgentId = (i < availableAgents.size()) ? availableAgents.get(i).getId() : null;

            String idempotencyKey = "CALL-PRED-" + campaignId + "-" + borrower.getId() + "-" + UUID.randomUUID();
            CallRecord call = new CallRecord(campaignId, borrower.getId(), assignedAgentId, idempotencyKey);
            CallRecord savedCall = callRecordRepository.save(call);

            if (assignedAgentId != null) {
                boolean agentReserved = agentService.reserveAgent(assignedAgentId, savedCall.getId());
                if (!agentReserved) {
                    savedCall.setAgentId(null); // Will bridge upon answer
                }
            }

            boolean borrowerReserved = borrowerService.reserveBorrower(borrower.getId());
            if (!borrowerReserved) {
                if (savedCall.getAgentId() != null) {
                    agentService.releaseAgent(savedCall.getAgentId());
                }
                savedCall.setStatus(CallStatus.CANCELLED);
                savedCall.setFailureReason("BORROWER_UNAVAILABLE_CONCURRENCY");
                callRecordRepository.save(savedCall);
                continue;
            }

            String outboxPayload = String.format(
                    "{\"callId\":%d,\"campaignId\":%d,\"borrowerId\":%d,\"agentId\":%s,\"phoneNumber\":\"%s\",\"idempotencyKey\":\"%s\"}",
                    savedCall.getId(), campaignId, borrower.getId(),
                    savedCall.getAgentId() == null ? "null" : savedCall.getAgentId(),
                    borrower.getPhoneNumber(), idempotencyKey
            );
            OutboxEvent outboxEvent = new OutboxEvent(String.valueOf(savedCall.getId()), "DISPATCH_CALL", outboxPayload);
            outboxEventRepository.save(outboxEvent);

            allocatedCalls.add(savedCall);
        }

        return allocatedCalls;
    }
}
