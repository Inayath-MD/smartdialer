package com.smartdialer.service.dialer;

import com.smartdialer.dto.DialerRunResponse;
import com.smartdialer.dto.PacingRecommendation;
import com.smartdialer.dto.SafetyCheckResult;
import com.smartdialer.entity.CallRecord;
import com.smartdialer.entity.Campaign;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.enums.CampaignStatus;
import com.smartdialer.enums.DialingMode;
import com.smartdialer.enums.SafetyDecision;
import com.smartdialer.exception.ResourceNotFoundException;
import com.smartdialer.provider.TelecomProvider;
import com.smartdialer.provider.TelecomProviderRegistry;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.repository.CampaignRepository;
import com.smartdialer.service.allocation.CallAllocatorService;
import com.smartdialer.service.allocation.OutboxDispatcherService;
import com.smartdialer.service.pacing.PredictivePacingService;
import com.smartdialer.service.safety.SafetyControllerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Predictive Dialer Service.
 * 
 * Invariant: Predictive pacing NEVER directly initiates calls.
 * All proposed dialing calls MUST be submitted to the independent Safety Controller.
 */
@Service
public class PredictiveDialerService {

    private static final Logger log = LoggerFactory.getLogger(PredictiveDialerService.class);

    private final CampaignRepository campaignRepository;
    private final AgentRepository agentRepository;
    private final CallRecordRepository callRecordRepository;
    private final TelecomProviderRegistry telecomProviderRegistry;
    private final PredictivePacingService predictivePacingService;
    private final SafetyControllerService safetyControllerService;
    private final CallAllocatorService callAllocatorService;
    private final OutboxDispatcherService outboxDispatcherService;
    private final ProgressiveDialerService progressiveDialerService;

    public PredictiveDialerService(
            CampaignRepository campaignRepository,
            AgentRepository agentRepository,
            CallRecordRepository callRecordRepository,
            TelecomProviderRegistry telecomProviderRegistry,
            PredictivePacingService predictivePacingService,
            SafetyControllerService safetyControllerService,
            CallAllocatorService callAllocatorService,
            OutboxDispatcherService outboxDispatcherService,
            ProgressiveDialerService progressiveDialerService
    ) {
        this.campaignRepository = campaignRepository;
        this.agentRepository = agentRepository;
        this.callRecordRepository = callRecordRepository;
        this.telecomProviderRegistry = telecomProviderRegistry;
        this.predictivePacingService = predictivePacingService;
        this.safetyControllerService = safetyControllerService;
        this.callAllocatorService = callAllocatorService;
        this.outboxDispatcherService = outboxDispatcherService;
        this.progressiveDialerService = progressiveDialerService;
    }

    public DialerRunResponse runPredictiveDialing(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            log.info("Campaign {} is not ACTIVE (status={}). Skipping predictive dialing run.", campaignId, campaign.getStatus());
            return new DialerRunResponse(
                    campaignId,
                    DialingMode.PREDICTIVE,
                    0, 0, 0, 0,
                    SafetyDecision.REJECT,
                    "Campaign is not ACTIVE",
                    Instant.now()
            );
        }

        // Gather real-time campaign & agent metrics
        long availableAgents = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.AVAILABLE);
        long reservedAgents = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.RESERVED);
        long connectedAgents = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.CONNECTED);
        long ringingCalls = callRecordRepository.countByCampaignIdAndStatus(campaignId, CallStatus.RINGING);

        TelecomProvider.ProviderStatus providerStatus = telecomProviderRegistry.getActiveProvider().getProviderStatus();

        PredictivePacingService.PacingInput pacingInput = new PredictivePacingService.PacingInput(
                campaignId,
                availableAgents,
                reservedAgents,
                connectedAgents,
                ringingCalls,
                campaign.getMaxConcurrentCalls(),
                campaign.getTargetAbandonmentRate(),
                providerStatus.failureRate(),
                providerStatus.currentLatencyMs()
        );

        // Step 1: Statistical Recommendation (Advisory Only)
        PacingRecommendation recommendation = predictivePacingService.calculateRecommendation(pacingInput);
        int proposedCalls = recommendation.recommendedCalls();

        log.info("[PredictiveDialer] Campaign {}: Pacing Engine recommended {} calls (Reason: '{}')",
                campaignId, proposedCalls, recommendation.reasoning());

        // Step 2: MANDATORY Safety Controller Evaluation
        // Predictive engine cannot bypass the Safety Controller!
        SafetyCheckResult safetyResult = safetyControllerService.evaluateDialingRequest(
                campaignId,
                proposedCalls,
                DialingMode.PREDICTIVE
        );

        // Check for Progressive Fallback
        if (safetyResult.decision() == SafetyDecision.PROGRESSIVE_FALLBACK) {
            log.warn("[PredictiveDialer] Safety Controller ordered PROGRESSIVE_FALLBACK for Campaign {}. Routing to Progressive Dialer.", campaignId);
            return progressiveDialerService.runProgressiveDialing(campaignId);
        }

        int approvedCalls = safetyResult.approvedCalls();
        if (approvedCalls <= 0 || safetyResult.decision() == SafetyDecision.REJECT) {
            return new DialerRunResponse(
                    campaignId,
                    DialingMode.PREDICTIVE,
                    proposedCalls,
                    0, 0, 0,
                    safetyResult.decision(),
                    safetyResult.reasoning(),
                    Instant.now()
            );
        }

        // Step 3: Transactional Allocation
        List<CallRecord> allocated = callAllocatorService.allocatePredictiveCalls(campaignId, approvedCalls);

        // Step 4: Outbox Dispatch
        int dispatched = outboxDispatcherService.processPendingDispatches();

        log.info("[PredictiveDialer] Campaign {}: Proposed={}, Approved={}, Allocated={}, Dispatched={}",
                campaignId, proposedCalls, approvedCalls, allocated.size(), dispatched);

        return new DialerRunResponse(
                campaignId,
                DialingMode.PREDICTIVE,
                proposedCalls,
                approvedCalls,
                allocated.size(),
                dispatched,
                safetyResult.decision(),
                safetyResult.reasoning(),
                Instant.now()
        );
    }
}
