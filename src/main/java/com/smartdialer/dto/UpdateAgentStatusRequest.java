package com.smartdialer.dto;

import com.smartdialer.enums.AgentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAgentStatusRequest(
        @NotNull(message = "Agent status is required")
        AgentStatus status
) {}
