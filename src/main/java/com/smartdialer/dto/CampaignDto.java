package com.smartdialer.dto;

import com.smartdialer.enums.CampaignStatus;
import com.smartdialer.enums.DialingMode;
import java.time.Instant;

public record CampaignDto(
        Long id,
        String name,
        CampaignStatus status,
        DialingMode dialingMode,
        int maxConcurrentCalls,
        double targetAbandonmentRate,
        int maxAttemptsPerBorrower,
        int cooldownPeriodSeconds,
        Instant createdAt
) {}
