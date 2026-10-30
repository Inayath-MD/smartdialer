package com.smartdialer.service.dialer;

import com.smartdialer.dto.DialerRunResponse;
import com.smartdialer.dto.SafetyCheckResult;
import com.smartdialer.entity.CallRecord;
import com.smartdialer.entity.Campaign;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CampaignStatus;
import com.smartdialer.enums.DialingMode;
import com.smartdialer.enums.SafetyDecision;
import com.smartdialer.exception.ResourceNotFoundException;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.CampaignRepository;
import com.smartdialer.service.allocation.CallAllocatorService;
import com.smartdialer.service.allocation.OutboxDispatcherService;
import com.smartdialer.service.safety.SafetyControllerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Progressive Dialer Service.
 * 
 * Invariant: ONE AVAILABLE AGENT = AT MOST ONE AGENT-BOUND OUTBOUND CALL.
 * All dialing decisions MUST pass through the independent Safety Controller.
 */
@Service
public class ProgressiveDialerService {

    private static final Logger log = LoggerFactory.getLogger(ProgressiveDialerService.class);

    private final CampaignRepository campaignRepository;
    private final AgentRepository agentRepository;
    private final SafetyControllerService safetyControllerService;
    private final CallAllocatorService callAllocatorService;
    private final OutboxDispatcherService outboxDispatcherService;

    public ProgressiveDialerService(
            CampaignRepository campaignRepository,
            AgentRepository agentRepository,
            SafetyControllerService safetyControllerService,
            CallAllocatorService callAllocatorService,
            OutboxDispatcherService outboxDispatcherService
    ) {
        this.campaignRepository = campaignRepository;
        this.agentRepository = agentRepository;
        this.safetyControllerService = safetyControllerService;
        this.callAllocatorService = callAllocatorService;
        this.outboxDispatcherService = outboxDispatcherService;
    }

    public DialerRunResponse runProgressiveDialing(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            log.info("Campaign {} is not ACTIVE (status={}). Skipping progressive dialing run.", campaignId, campaign.getStatus());
            return new DialerRunResponse(
                    campaignId,
                    DialingMode.PROGRESSIVE,
                    0, 0, 0, 0,
                    SafetyDecision.REJECT,
                    "Campaign is not ACTIVE",
                    Instant.now()
            );
        }

        // Count available agents - strict progressive candidate upper bound
        long availableAgents = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.AVAILABLE);
        int requestedCalls = (int) availableAgents;

        // Step 1: MANDATORY Safety Controller Evaluation
        SafetyCheckResult safetyResult = safetyControllerService.evaluateDialingRequest(
                campaignId,
                requestedCalls,
                DialingMode.PROGRESSIVE
        );

        int approvedCalls = safetyResult.approvedCalls();
        if (approvedCalls <= 0 || safetyResult.decision() == SafetyDecision.REJECT) {
            return new DialerRunResponse(
                    campaignId,
                    DialingMode.PROGRESSIVE,
                    requestedCalls,
                    0, 0, 0,
                    safetyResult.decision(),
                    safetyResult.reasoning(),
                    Instant.now()
            );
        }

        // Step 2: Transactional Allocation and Outbox generation
        List<CallRecord> allocated = callAllocatorService.allocateProgressiveCalls(campaignId, approvedCalls);

        // Step 3: Reliable non-blocking outbox dispatch
        int dispatched = outboxDispatcherService.processPendingDispatches();

        log.info("[ProgressiveDialer] Campaign {}: Requested={}, Approved={}, Allocated={}, Dispatched={}",
                campaignId, requestedCalls, approvedCalls, allocated.size(), dispatched);

        return new DialerRunResponse(
                campaignId,
                DialingMode.PROGRESSIVE,
                requestedCalls,
                approvedCalls,
                allocated.size(),
                dispatched,
                safetyResult.decision(),
                safetyResult.reasoning(),
                Instant.now()
        );
    }
}
