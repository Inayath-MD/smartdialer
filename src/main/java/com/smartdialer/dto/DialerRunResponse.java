package com.smartdialer.dto;

import com.smartdialer.enums.DialingMode;
import com.smartdialer.enums.SafetyDecision;
import java.time.Instant;

public record DialerRunResponse(
        Long campaignId,
        DialingMode dialingMode,
        int requestedCalls,
        int approvedCalls,
        int allocatedCalls,
        int dispatchedCalls,
        SafetyDecision decision,
        String reasoning,
        Instant executedAt
) {}
