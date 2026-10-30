package com.smartdialer.dto;

import com.smartdialer.enums.ProviderEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ProviderCallbackEvent(
        @NotBlank(message = "Provider event ID is required")
        String providerEventId,

        @NotBlank(message = "Provider call ID is required")
        String providerCallId,

        @NotNull(message = "Event type is required")
        ProviderEventType eventType,

        Instant timestamp,
        String failureReason
) {}
