package com.smartdialer.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAgentRequest(
        @NotBlank(message = "Agent name is required")
        String name,
        Long campaignId
) {}
