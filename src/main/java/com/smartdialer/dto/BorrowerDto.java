package com.smartdialer.dto;

import com.smartdialer.enums.BorrowerStatus;
import java.time.Instant;

public record BorrowerDto(
        Long id,
        String name,
        String phoneNumber,
        Long campaignId,
        BorrowerStatus status,
        int attemptCount,
        Instant lastAttemptAt,
        Instant cooldownUntil
) {}
