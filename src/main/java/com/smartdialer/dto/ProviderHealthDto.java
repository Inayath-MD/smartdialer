package com.smartdialer.dto;

public record ProviderHealthDto(
        String providerName,
        boolean healthy,
        double failureRate,
        long averageLatencyMs,
        boolean circuitBreakerOpen,
        int consecutiveFailures
) {}
