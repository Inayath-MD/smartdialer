package com.smartdialer.dto;

import com.smartdialer.enums.DialingMode;
import java.time.Instant;
import java.util.List;

public record SimulationResultDto(
        String simulationId,
        String scenarioName,
        DialingMode dialingMode,
        int totalAgents,
        int totalBorrowers,
        int totalCallsInitiated,
        int totalCallsAnswered,
        int totalConnectedCalls,
        int totalCompletedCalls,
        int failedCalls,
        int abandonedCalls,
        double agentUtilizationPercentage,
        double averageAgentIdleTimeSeconds,
        int providerFailureCount,
        int safetyApprovals,
        int safetyReductions,
        int safetyRejections,
        int progressiveFallbackCount,
        long totalDialingTimeMs,
        boolean zeroOrphanInvariantPreserved,
        List<String> invariantViolations,
        Instant completedAt
) {}
