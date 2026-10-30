package com.smartdialer.dto;

import com.smartdialer.enums.DialingMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SimulationRunRequest(
        @Min(value = 1, message = "At least 1 agent required")
        @Max(value = 10000, message = "Max 10000 agents allowed")
        int numberOfAgents,

        @Min(value = 1, message = "At least 1 borrower required")
        int numberOfBorrowers,

        @Min(value = 0, message = "Answer rate cannot be negative")
        @Max(value = 1, message = "Answer rate cannot exceed 1.0")
        double answerRate,

        @Min(value = 1, message = "Average talk time must be at least 1s")
        int averageTalkTimeSeconds,

        @Min(value = 0, message = "Provider latency cannot be negative")
        int providerLatencyMs,

        @Min(value = 0, message = "Provider failure rate cannot be negative")
        @Max(value = 1, message = "Provider failure rate cannot exceed 1.0")
        double providerFailureRate,

        @NotNull(message = "Dialing mode is required")
        DialingMode dialingMode,

        @Min(value = 5, message = "Simulation duration must be at least 5s")
        int simulationDurationSeconds,

        boolean dynamicAnswerRate,

        String scenarioName
) {}
