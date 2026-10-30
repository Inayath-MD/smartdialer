package com.smartdialer.dto;

import com.smartdialer.enums.CallStatus;
import java.time.Instant;

public record CallRecordDto(
        Long id,
        Long campaignId,
        Long borrowerId,
        Long agentId,
        String providerCallId,
        String providerName,
        CallStatus status,
        Instant initiatedAt,
        Instant answeredAt,
        Instant completedAt,
        String failureReason,
        String idempotencyKey
) {}
