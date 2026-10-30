package com.smartdialer.dto;

import java.time.Instant;

public record PacingRecommendation(
        Long campaignId,
        int recommendedCalls,
        double estimatedAnswerRate,
        double expectedAnswers,
        double confidence,
        String reasoning,
        Instant timestamp
) {}
