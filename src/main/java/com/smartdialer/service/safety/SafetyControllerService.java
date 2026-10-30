package com.smartdialer.service.safety;

import com.smartdialer.dto.SafetyCheckResult;
import com.smartdialer.entity.Campaign;
import com.smartdialer.entity.SafetyDecisionRecord;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.enums.DialingMode;
import com.smartdialer.enums.SafetyDecision;
import com.smartdialer.exception.ResourceNotFoundException;
import com.smartdialer.exception.SafetyViolationException;
import com.smartdialer.provider.TelecomProvider;
import com.smartdialer.provider.TelecomProviderRegistry;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.repository.CampaignRepository;
import com.smartdialer.repository.SafetyDecisionRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class SafetyControllerService {

    private static final Logger log = LoggerFactory.getLogger(SafetyControllerService.class);

    private final AgentRepository agentRepository;
    private final CallRecordRepository callRecordRepository;
    private final CampaignRepository campaignRepository;
    private final SafetyDecisionRecordRepository safetyDecisionRecordRepository;
    private final TelecomProviderRegistry telecomProviderRegistry;

    public SafetyControllerService(
            AgentRepository agentRepository,
            CallRecordRepository callRecordRepository,
            CampaignRepository campaignRepository,
            SafetyDecisionRecordRepository safetyDecisionRecordRepository,
            TelecomProviderRegistry telecomProviderRegistry
    ) {
        this.agentRepository = agentRepository;
        this.callRecordRepository = callRecordRepository;
        this.campaignRepository = campaignRepository;
        this.safetyDecisionRecordRepository = safetyDecisionRecordRepository;
        this.telecomProviderRegistry = telecomProviderRegistry;
    }

    /**
     * Independent evaluation of dialing safety against authoritative database state.
     */
    @Transactional
    public SafetyCheckResult evaluateDialingRequest(Long campaignId, int requestedCalls, DialingMode mode) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with id: " + campaignId));

        // Authoritative Database State Read
        long availableAgents = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.AVAILABLE);
        long reservedAgents = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.RESERVED);
        long connectedAgents = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.CONNECTED);

        // Active Calls State Read
        long ringingCalls = callRecordRepository.countByCampaignIdAndStatus(campaignId, CallStatus.RINGING);
        long initiatedCalls = callRecordRepository.countByCampaignIdAndStatus(campaignId, CallStatus.INITIATED);
        long connectedCalls = callRecordRepository.countByCampaignIdAndStatus(campaignId, CallStatus.CONNECTED);

        long activeCallsTotal = ringingCalls + initiatedCalls + connectedCalls;
        int maxConcurrent = campaign.getMaxConcurrentCalls();
        int remainingConcurrencyHeadroom = (int) Math.max(0, maxConcurrent - activeCallsTotal);

        // Check Provider Health
        TelecomProvider activeProvider = telecomProviderRegistry.getActiveProvider();
        TelecomProvider.ProviderStatus providerStatus = activeProvider.getProviderStatus();

        SafetyDecision decision;
        int approvedCalls;
        String reasoning;

        // Rule 1: Zero capacity or campaign headroom exhausted
        if (remainingConcurrencyHeadroom <= 0) {
            decision = SafetyDecision.REJECT;
            approvedCalls = 0;
            reasoning = String.format("Rejected: Campaign concurrency limit reached (%d/%d active calls).",
                    activeCallsTotal, maxConcurrent);
            return recordAndReturn(campaignId, requestedCalls, approvedCalls, decision, reasoning,
                    availableAgents, reservedAgents, ringingCalls, connectedCalls, maxConcurrent);
        }

        // Rule 2: Critical Provider Outage
        if (!providerStatus.operational() || providerStatus.failureRate() > 0.25) {
            if (mode == DialingMode.PREDICTIVE) {
                // In predictive mode, provider degradation triggers fallback to conservative progressive mode
                decision = SafetyDecision.PROGRESSIVE_FALLBACK;
                approvedCalls = (int) Math.min(availableAgents, remainingConcurrencyHeadroom);
                reasoning = String.format("Provider degraded (failure rate %.1f%%). Fallback from Predictive to Progressive mode approved for %d calls.",
                        providerStatus.failureRate() * 100, approvedCalls);
            } else {
                // Progressive mode clamps strictly to available agents
                approvedCalls = (int) Math.min(availableAgents, remainingConcurrencyHeadroom);
                decision = approvedCalls > 0 ? SafetyDecision.APPROVE : SafetyDecision.REJECT;
                reasoning = String.format("Provider degraded. Progressive dialing allowed strictly for %d available agents.", approvedCalls);
            }
            return recordAndReturn(campaignId, requestedCalls, approvedCalls, decision, reasoning,
                    availableAgents, reservedAgents, ringingCalls, connectedCalls, maxConcurrent);
        }

        // Rule 3: Progressive Dialing Mode Enforcements
        // Invariant: ONE AVAILABLE AGENT = AT MOST ONE AGENT-BOUND CALL
        if (mode == DialingMode.PROGRESSIVE) {
            int safeSlots = (int) Math.min(availableAgents, remainingConcurrencyHeadroom);
            if (safeSlots <= 0) {
                decision = SafetyDecision.REJECT;
                approvedCalls = 0;
                reasoning = String.format("Rejected: Zero available agents for campaign %d (available: %d, reserved: %d).",
                        campaignId, availableAgents, reservedAgents);
            } else if (requestedCalls > safeSlots) {
                decision = SafetyDecision.REDUCE;
                approvedCalls = safeSlots;
                reasoning = String.format("Reduced from %d to %d calls to match available agents (%d) and concurrency limit (%d).",
                        requestedCalls, safeSlots, availableAgents, maxConcurrent);
            } else {
                decision = SafetyDecision.APPROVE;
                approvedCalls = requestedCalls;
                reasoning = String.format("Approved %d progressive calls for %d available agents.", approvedCalls, availableAgents);
            }
            return recordAndReturn(campaignId, requestedCalls, approvedCalls, decision, reasoning,
                    availableAgents, reservedAgents, ringingCalls, connectedCalls, maxConcurrent);
        }

        // Rule 4: Predictive Dialing Mode Enforcements
        // Maximum overdial multiplier cannot exceed 2.0x total staff or available agents + safe ringing buffer
        double maxSafeOverdial = Math.max(availableAgents * 2.0, availableAgents + 2);
        int maxSafePredictiveSlots = (int) Math.min(maxSafeOverdial, remainingConcurrencyHeadroom);

        if (requestedCalls <= 0) {
            decision = SafetyDecision.REJECT;
            approvedCalls = 0;
            reasoning = "Rejected: Predictive engine requested 0 calls.";
        } else if (maxSafePredictiveSlots <= 0) {
            decision = SafetyDecision.REJECT;
            approvedCalls = 0;
            reasoning = String.format("Rejected: Insufficient safe slots (availableAgents: %d, ringing: %d, active: %d/%d).",
                    availableAgents, ringingCalls, activeCallsTotal, maxConcurrent);
        } else if (requestedCalls > maxSafePredictiveSlots) {
            decision = SafetyDecision.REDUCE;
            approvedCalls = maxSafePredictiveSlots;
            reasoning = String.format("Reduced from %d to %d predictive calls to prevent exceeding safe ceiling (active headroom: %d).",
                    requestedCalls, approvedCalls, remainingConcurrencyHeadroom);
        } else {
            decision = SafetyDecision.APPROVE;
            approvedCalls = requestedCalls;
            reasoning = String.format("Approved %d predictive calls (availableAgents: %d, ringing: %d).",
                    approvedCalls, availableAgents, ringingCalls);
        }

        return recordAndReturn(campaignId, requestedCalls, approvedCalls, decision, reasoning,
                availableAgents, reservedAgents, ringingCalls, connectedCalls, maxConcurrent);
    }

    /**
     * Critical final transactional boundary guard.
     * Prevents race conditions where state changed between initial safety check and allocation.
     */
    @Transactional
    public void enforceTransactionalSafety(Long campaignId, int countToAllocate, DialingMode mode) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        long currentActive = callRecordRepository.countByCampaignIdAndStatusIn(
                campaignId,
                Set.of(CallStatus.INITIATED, CallStatus.RINGING, CallStatus.ANSWERED, CallStatus.CONNECTED)
        );

        if (currentActive + countToAllocate > campaign.getMaxConcurrentCalls()) {
            throw new SafetyViolationException(String.format(
                    "Safety violation: Allocating %d calls would exceed max campaign concurrency (%d + %d > %d)",
                    countToAllocate, currentActive, countToAllocate, campaign.getMaxConcurrentCalls()
            ));
        }

        if (mode == DialingMode.PROGRESSIVE) {
            long available = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.AVAILABLE);
            if (available < countToAllocate) {
                throw new SafetyViolationException(String.format(
                        "Safety violation: Progressive mode requires %d available agents, but only %d found in transaction.",
                        countToAllocate, available
                ));
            }
        }
    }

    private SafetyCheckResult recordAndReturn(
            Long campaignId, int requestedCalls, int approvedCalls,
            SafetyDecision decision, String reasoning,
            long availableAgents, long reservedAgents,
            long ringingCalls, long connectedCalls, int maxConcurrent
    ) {
        SafetyDecisionRecord record = new SafetyDecisionRecord(
                campaignId, requestedCalls, approvedCalls, decision, reasoning
        );
        safetyDecisionRecordRepository.save(record);
        log.info("[SafetyController] Campaign {}: Decision={} Approved={}/{} Reason='{}'",
                campaignId, decision, approvedCalls, requestedCalls, reasoning);

        return new SafetyCheckResult(
                campaignId,
                requestedCalls,
                approvedCalls,
                decision,
                reasoning,
                availableAgents,
                reservedAgents,
                ringingCalls,
                connectedCalls,
                maxConcurrent,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public List<SafetyDecisionRecord> getRecentDecisions(Long campaignId, int limit) {
        return safetyDecisionRecordRepository.findByCampaignIdOrderByCreatedAtDesc(
                campaignId,
                org.springframework.data.domain.PageRequest.of(0, limit)
        );
    }
}
