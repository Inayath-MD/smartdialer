package com.smartdialer.dto;

import jakarta.validation.constraints.NotNull;

public record DialerRunRequest(
        @NotNull(message = "Campaign ID is required")
        Long campaignId
) {}
