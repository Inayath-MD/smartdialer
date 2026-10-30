package com.smartdialer;

import com.smartdialer.dto.SafetyCheckResult;
import com.smartdialer.entity.Agent;
import com.smartdialer.entity.Campaign;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CampaignStatus;
import com.smartdialer.enums.DialingMode;
import com.smartdialer.enums.SafetyDecision;
import com.smartdialer.provider.MockProviderA;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.CampaignRepository;
import com.smartdialer.service.safety.SafetyControllerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class SafetyControllerTest {

    @Autowired
    private SafetyControllerService safetyControllerService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private MockProviderA mockProviderA;

    @Test
    @DisplayName("MANDATORY TEST 10: Predictive engine cannot bypass Safety Controller")
    public void testPredictiveEngineCannotBypassSafetyController() {
        Campaign campaign = new Campaign("Safety-Bypass-Test", DialingMode.PREDICTIVE, 5);
        campaign.setStatus(CampaignStatus.ACTIVE);
        Campaign saved = campaignRepository.save(campaign);
        Long campaignId = saved.getId();

        // Seed 2 available agents
        agentRepository.save(new Agent("Agent-S1", AgentStatus.AVAILABLE, campaignId));
        agentRepository.save(new Agent("Agent-S2", AgentStatus.AVAILABLE, campaignId));

        // Predictive engine recklessly requests 25 calls
        int recklessProposedCalls = 25;

        SafetyCheckResult result = safetyControllerService.evaluateDialingRequest(
                campaignId, recklessProposedCalls, DialingMode.PREDICTIVE
        );

        // Assert: Safety Controller MUST REDUCE the approved calls to the safe limit
        assertEquals(SafetyDecision.REDUCE, result.decision());
        assertTrue(result.approvedCalls() <= 5, "Approved calls cannot exceed campaign concurrency limit (5)");
        assertTrue(result.approvedCalls() < recklessProposedCalls, "Reckless request must be strictly curtailed");
    }

    @Test
    @DisplayName("MANDATORY TEST 11: Provider outage activates fallback")
    public void testProviderOutageActivatesFallback() {
        Campaign campaign = new Campaign("Provider-Outage-Test", DialingMode.PREDICTIVE, 10);
        campaign.setStatus(CampaignStatus.ACTIVE);
        Campaign saved = campaignRepository.save(campaign);
        Long campaignId = saved.getId();

        // Seed 3 available agents
        for (int i = 1; i <= 3; i++) {
            agentRepository.save(new Agent("Agent-Outage-" + i, AgentStatus.AVAILABLE, campaignId));
        }

        // Simulate provider critical degradation (failure rate = 35%)
        mockProviderA.setFailureRate(0.35);

        try {
            SafetyCheckResult result = safetyControllerService.evaluateDialingRequest(
                    campaignId, 8, DialingMode.PREDICTIVE
            );

            // Assert: Safety Controller must order PROGRESSIVE_FALLBACK
            assertEquals(SafetyDecision.PROGRESSIVE_FALLBACK, result.decision(),
                    "Critical provider outage must trigger PROGRESSIVE_FALLBACK");
            assertTrue(result.approvedCalls() <= 3, "Fallback must be bounded by available agents");
        } finally {
            // Restore provider health
            mockProviderA.setFailureRate(0.01);
        }
    }

    @Test
    @DisplayName("MANDATORY TEST 13: Campaign capacity is respected under concurrency")
    public void testCampaignCapacityIsRespectedUnderConcurrency() {
        Campaign campaign = new Campaign("Capacity-Limit-Test", DialingMode.PROGRESSIVE, 3);
        campaign.setStatus(CampaignStatus.ACTIVE);
        Campaign saved = campaignRepository.save(campaign);
        Long campaignId = saved.getId();

        // Seed 5 available agents (more than campaign maxConcurrentCalls of 3)
        for (int i = 1; i <= 5; i++) {
            agentRepository.save(new Agent("Agent-Cap-" + i, AgentStatus.AVAILABLE, campaignId));
        }

        SafetyCheckResult result = safetyControllerService.evaluateDialingRequest(
                campaignId, 5, DialingMode.PROGRESSIVE
        );

        // Assert: Even with 5 agents, approved calls must NOT exceed campaign capacity of 3
        assertEquals(3, result.approvedCalls(), "Approved calls must be capped by campaign maxConcurrentCalls");
        assertEquals(SafetyDecision.REDUCE, result.decision());
    }
}
