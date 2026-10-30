package com.smartdialer.service.pacing;

import com.smartdialer.dto.PacingRecommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Predictive Pacing Engine.
 * 
 * Estimates the safe number of calls to propose based on transparent statistical modeling.
 * IMPORTANT: This component only produces recommendations. It NEVER initiates calls
 * and NEVER directly interacts with TelecomProvider or CallAllocator.
 */
@Service
public class PredictivePacingService {

    private static final Logger log = LoggerFactory.getLogger(PredictivePacingService.class);

    // Exponentially Weighted Moving Average (EWMA) tracker per campaign
    private final ConcurrentHashMap<Long, CampaignStats> campaignStatsMap = new ConcurrentHashMap<>();

    public record PacingInput(
            Long campaignId,
            long availableAgents,
            long reservedAgents,
            long connectedAgents,
            long ringingCalls,
            int maxConcurrentCalls,
            double targetAbandonmentRate,
            double currentProviderFailureRate,
            long currentProviderLatencyMs
    ) {}

    public PacingRecommendation calculateRecommendation(PacingInput input) {
        CampaignStats stats = campaignStatsMap.computeIfAbsent(input.campaignId(), k -> new CampaignStats());

        double answerRate = stats.getEstimatedAnswerRate();
        double avgTalkTimeSec = stats.getAverageTalkTimeSeconds();
        double confidence = stats.calculateConfidence();

        // Step 1: Health check on provider metrics
        if (input.currentProviderFailureRate() > 0.25) {
            String reasoning = String.format(
                    "Predictive pacing halted: Provider failure rate (%.1f%%) exceeds critical safety threshold of 25%%.",
                    input.currentProviderFailureRate() * 100
            );
            return new PacingRecommendation(
                    input.campaignId(),
                    0,
                    answerRate,
                    0.0,
                    confidence,
                    reasoning,
                    Instant.now()
            );
        }

        // Step 2: Estimate agent capacity freed up during ring cycle (~25 seconds)
        double estimatedRingDurationSec = 25.0;
        double turnoverRate = avgTalkTimeSec > 0 ? (estimatedRingDurationSec / avgTalkTimeSec) : 0.0;
        double expectedFreedAgents = Math.min(input.connectedAgents(), input.connectedAgents() * turnoverRate);

        // Net agent capacity available to absorb answers
        double totalExpectedAgentCapacity = input.availableAgents() + (expectedFreedAgents * 0.75); // 25% safety haircut on turnover

        // Step 3: Account for answers already expected from in-flight ringing calls
        double inFlightExpectedAnswers = input.ringingCalls() * answerRate;
        double netAvailableAgentCapacity = Math.max(0.0, totalExpectedAgentCapacity - inFlightExpectedAnswers);

        // If no capacity or negative, propose 0 calls
        if (netAvailableAgentCapacity <= 0.05) {
            String reasoning = String.format(
                    "Zero calls proposed: In-flight ringing calls (%d) with answer rate %.1f%% already saturate expected agent capacity (%.1f agents).",
                    input.ringingCalls(), answerRate * 100, totalExpectedAgentCapacity
            );
            return new PacingRecommendation(
                    input.campaignId(),
                    0,
                    answerRate,
                    0.0,
                    confidence,
                    reasoning,
                    Instant.now()
            );
        }

        // Step 4: Statistical pacing formula
        // RawProposed = NetCapacity / AnswerRate
        double safeAnswerRate = Math.max(0.05, Math.min(0.95, answerRate));
        double rawProposed = netAvailableAgentCapacity / safeAnswerRate;

        // Step 5: Safety and Latency adjustments
        double safetyMargin = 0.85; // 15% conservative safety buffer
        
        // Latency penalty: reduce pacing if provider latency > 500ms
        double latencyFactor = 1.0;
        if (input.currentProviderLatencyMs() > 1000) {
            latencyFactor = 0.5;
        } else if (input.currentProviderLatencyMs() > 500) {
            latencyFactor = 0.75;
        }

        // Target abandonment rate adaptation
        double abandonmentSafetyFactor = input.targetAbandonmentRate() <= 0.03 ? 0.90 : 1.0;

        double adjustedCalls = rawProposed * safetyMargin * latencyFactor * abandonmentSafetyFactor * confidence;
        int recommendedCalls = (int) Math.floor(adjustedCalls);

        // Clamp to campaign maximum concurrent headroom
        long currentInFlight = input.ringingCalls() + input.connectedAgents();
        int remainingCampaignHeadroom = (int) Math.max(0, input.maxConcurrentCalls() - currentInFlight);
        recommendedCalls = Math.min(recommendedCalls, remainingCampaignHeadroom);
        recommendedCalls = Math.max(0, recommendedCalls);

        double expectedAnswers = recommendedCalls * safeAnswerRate;

        String reasoning = String.format(
                "Paced %d calls (Raw: %.1f, NetCap: %.1f, AnsRate: %.1f%%, Conf: %.2f, LatencyFactor: %.2f, CampaignHeadroom: %d)",
                recommendedCalls, rawProposed, netAvailableAgentCapacity, safeAnswerRate * 100, confidence, latencyFactor, remainingCampaignHeadroom
        );

        log.debug("[PredictivePacing] Campaign {}: {}", input.campaignId(), reasoning);

        return new PacingRecommendation(
                input.campaignId(),
                recommendedCalls,
                safeAnswerRate,
                expectedAnswers,
                confidence,
                reasoning,
                Instant.now()
        );
    }

    public void recordCallOutcome(Long campaignId, boolean answered, long durationSeconds) {
        CampaignStats stats = campaignStatsMap.computeIfAbsent(campaignId, k -> new CampaignStats());
        stats.recordCall(answered, durationSeconds);
    }

    public void updateStats(Long campaignId, double answerRate, double avgTalkTimeSec) {
        CampaignStats stats = campaignStatsMap.computeIfAbsent(campaignId, k -> new CampaignStats());
        stats.setAnswerRate(answerRate);
        stats.setAverageTalkTime(avgTalkTimeSec);
    }

    public static class CampaignStats {
        private final AtomicInteger totalAttempts = new AtomicInteger(0);
        private final AtomicInteger totalAnswers = new AtomicInteger(0);
        private final AtomicLong totalTalkDurationSec = new AtomicLong(0);

        private volatile double answerRateEwma = 0.35; // Default prior answer rate
        private volatile double avgTalkTimeSecEwma = 120.0; // Default prior duration
        private static final double ALPHA = 0.1; // Smoothing factor

        public synchronized void recordCall(boolean answered, long durationSeconds) {
            totalAttempts.incrementAndGet();
            if (answered) {
                totalAnswers.incrementAndGet();
                if (durationSeconds > 0) {
                    totalTalkDurationSec.addAndGet(durationSeconds);
                    avgTalkTimeSecEwma = (ALPHA * durationSeconds) + ((1.0 - ALPHA) * avgTalkTimeSecEwma);
                }
                answerRateEwma = (ALPHA * 1.0) + ((1.0 - ALPHA) * answerRateEwma);
            } else {
                answerRateEwma = (ALPHA * 0.0) + ((1.0 - ALPHA) * answerRateEwma);
            }
        }

        public double getEstimatedAnswerRate() {
            return answerRateEwma;
        }

        public double getAverageTalkTimeSeconds() {
            return avgTalkTimeSecEwma;
        }

        public double calculateConfidence() {
            int attempts = totalAttempts.get();
            if (attempts < 10) return 0.65;
            if (attempts < 50) return 0.80;
            if (attempts < 200) return 0.90;
            return 0.98;
        }

        public void setAnswerRate(double rate) {
            this.answerRateEwma = rate;
        }

        public void setAverageTalkTime(double duration) {
            this.avgTalkTimeSecEwma = duration;
        }
    }
}
