package com.smartdialer;

import com.smartdialer.dto.DialerRunResponse;
import com.smartdialer.entity.Agent;
import com.smartdialer.entity.Borrower;
import com.smartdialer.entity.Campaign;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CampaignStatus;
import com.smartdialer.enums.DialingMode;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.BorrowerRepository;
import com.smartdialer.repository.CampaignRepository;
import com.smartdialer.service.dialer.ProgressiveDialerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ProgressiveDialerTest {

    @Autowired
    private ProgressiveDialerService progressiveDialerService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Test
    @DisplayName("MANDATORY TEST 3: Progressive dialing never exceeds available agent capacity")
    public void testProgressiveDialingNeverExceedsAvailableAgents() {
        // Arrange Campaign
        Campaign campaign = new Campaign("Progressive-Test-Campaign", DialingMode.PROGRESSIVE, 20);
        campaign.setStatus(CampaignStatus.ACTIVE);
        Campaign savedCampaign = campaignRepository.save(campaign);
        Long campaignId = savedCampaign.getId();

        // Seed exactly 3 AVAILABLE agents
        for (int i = 1; i <= 3; i++) {
            agentRepository.save(new Agent("ProgAgent-" + i, AgentStatus.AVAILABLE, campaignId));
        }

        // Seed 10 eligible borrowers
        for (int i = 1; i <= 10; i++) {
            borrowerRepository.save(new Borrower("Borrower-" + i, "+1555" + (1000 + i), campaignId));
        }

        // Act: Run progressive dialer
        DialerRunResponse response = progressiveDialerService.runProgressiveDialing(campaignId);

        // Assert: Approved and allocated calls CANNOT exceed available agent capacity (3)
        assertEquals(3, response.approvedCalls(), "Approved calls must equal available agent capacity");
        assertEquals(3, response.allocatedCalls(), "Allocated calls must equal exactly 3");

        // Verify remaining available agents is now 0
        long remainingAvailable = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.AVAILABLE);
        assertEquals(0, remainingAvailable, "All available agents should now be engaged");

        // Running again when 0 agents are available must allocate 0 calls
        DialerRunResponse secondRun = progressiveDialerService.runProgressiveDialing(campaignId);
        assertEquals(0, secondRun.allocatedCalls(), "Second run with 0 available agents must allocate 0 calls");
    }
}
