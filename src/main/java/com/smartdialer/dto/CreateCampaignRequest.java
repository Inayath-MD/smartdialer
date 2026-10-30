package com.smartdialer.dto;

import com.smartdialer.enums.DialingMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCampaignRequest(
        @NotBlank(message = "Campaign name is required")
        String name,

        @NotNull(message = "Dialing mode is required")
        DialingMode dialingMode,

        @Min(value = 1, message = "Max concurrent calls must be at least 1")
        Integer maxConcurrentCalls,

        @DecimalMin(value = "0.001", message = "Target abandonment rate must be positive")
        @DecimalMax(value = "0.50", message = "Target abandonment rate cannot exceed 50%")
        Double targetAbandonmentRate,

        @Min(value = 1, message = "Max attempts per borrower must be at least 1")
        Integer maxAttemptsPerBorrower,

        @Min(value = 0, message = "Cooldown period cannot be negative")
        Integer cooldownPeriodSeconds
) {}
