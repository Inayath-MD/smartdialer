package com.smartdialer;

import com.smartdialer.dto.PacingRecommendation;
import com.smartdialer.service.pacing.PredictivePacingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PacingEngineTest {

    private PredictivePacingService pacingService;

    @BeforeEach
    public void setUp() {
        pacingService = new PredictivePacingService();
    }

    @Test
    @DisplayName("Pacing Engine proposes calls based on answer rate and net agent capacity")
    public void testPacingCalculationWithHealthyProvider() {
        Long campaignId = 10L;
        // Seed campaign historical stats: 40% answer rate, 120s talk time
        pacingService.updateStats(campaignId, 0.40, 120.0);

        PredictivePacingService.PacingInput input = new PredictivePacingService.PacingInput(
                campaignId,
                10, // 10 available agents
                0,  // 0 reserved
                20, // 20 connected
                2,  // 2 ringing
                50, // max concurrent 50
                0.03, // 3% abandonment target
                0.01, // 1% provider failure
                30    // 30ms latency
        );

        PacingRecommendation rec = pacingService.calculateRecommendation(input);

        assertNotNull(rec);
        assertTrue(rec.recommendedCalls() > 0, "Healthy campaign with 10 free agents should recommend positive calls");
        assertTrue(rec.recommendedCalls() <= 50, "Recommended calls cannot exceed max headroom");
        assertEquals(0.40, rec.estimatedAnswerRate(), 0.01);
        assertNotNull(rec.reasoning());
        assertTrue(rec.confidence() > 0.5);
    }

    @Test
    @DisplayName("Pacing Engine halts predictive dialing when provider failure rate exceeds critical threshold")
    public void testPacingHaltsOnProviderDegradation() {
        Long campaignId = 20L;
        pacingService.updateStats(campaignId, 0.50, 90.0);

        PredictivePacingService.PacingInput criticalProviderInput = new PredictivePacingService.PacingInput(
                campaignId,
                10, 0, 0, 0,
                50, 0.03,
                0.30, // 30% provider failure (critical!)
                50
        );

        PacingRecommendation rec = pacingService.calculateRecommendation(criticalProviderInput);

        assertEquals(0, rec.recommendedCalls(), "Predictive pacing must recommend 0 calls when provider failure exceeds 25%");
        assertTrue(rec.reasoning().contains("Provider failure rate"));
    }

    @Test
    @DisplayName("Pacing Engine throttles recommendations when provider latency spikes")
    public void testPacingThrottlesOnHighLatency() {
        Long campaignId = 30L;
        pacingService.updateStats(campaignId, 0.50, 100.0);

        PredictivePacingService.PacingInput lowLatencyInput = new PredictivePacingService.PacingInput(
                campaignId, 10, 0, 0, 0, 50, 0.03, 0.01, 20
        );
        PacingRecommendation lowLatRec = pacingService.calculateRecommendation(lowLatencyInput);

        PredictivePacingService.PacingInput highLatencyInput = new PredictivePacingService.PacingInput(
                campaignId, 10, 0, 0, 0, 50, 0.03, 0.01, 1500 // 1.5s latency spike
        );
        PacingRecommendation highLatRec = pacingService.calculateRecommendation(highLatencyInput);

        assertTrue(highLatRec.recommendedCalls() < lowLatRec.recommendedCalls(),
                "High provider latency must throttle down the recommended calls");
    }
}
