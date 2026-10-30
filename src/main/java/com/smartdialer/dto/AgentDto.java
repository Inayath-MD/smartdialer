package com.smartdialer.dto;

import com.smartdialer.enums.AgentStatus;
import java.time.Instant;

public record AgentDto(
        Long id,
        String name,
        AgentStatus status,
        Instant lastHeartbeatAt,
        Long currentCallId,
        Long campaignId
) {}
