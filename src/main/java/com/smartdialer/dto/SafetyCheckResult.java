package com.smartdialer.dto;

import com.smartdialer.enums.SafetyDecision;
import java.time.Instant;

public record SafetyCheckResult(
        Long campaignId,
        int requestedCalls,
        int approvedCalls,
        SafetyDecision decision,
        String reasoning,
        long availableAgents,
        long reservedAgents,
        long ringingCalls,
        long connectedCalls,
        int maxConcurrentAllowed,
        Instant timestamp
) {}
