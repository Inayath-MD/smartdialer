package com.smartdialer.dto;

import com.smartdialer.enums.CampaignStatus;
import com.smartdialer.enums.DialingMode;

public record DialerStatusResponse(
        Long campaignId,
        CampaignStatus campaignStatus,
        DialingMode dialingMode,
        long availableAgents,
        long reservedAgents,
        long dialingAgents,
        long connectedAgents,
        long ringingCalls,
        long connectedCalls,
        long eligibleBorrowers,
        boolean circuitBreakerOpen
) {}
