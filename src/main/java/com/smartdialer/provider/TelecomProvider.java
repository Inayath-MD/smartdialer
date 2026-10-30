package com.smartdialer.provider;

import com.smartdialer.enums.CallStatus;
import com.smartdialer.enums.TelecomProviderType;

import java.time.Instant;

public interface TelecomProvider {

    TelecomProviderType getProviderType();

    InitiateCallResponse initiateCall(InitiateCallRequest request);

    ProviderStatus getProviderStatus();

    CallStatusResponse queryCallStatus(String providerCallId);

    void setFailureRate(double failureRate);

    void setBaseLatencyMs(long latencyMs);

    record InitiateCallRequest(
            Long callId,
            Long campaignId,
            String phoneNumber,
            String idempotencyKey
    ) {}

    record InitiateCallResponse(
            boolean success,
            String providerCallId,
            String errorCode,
            String errorMessage
    ) {}

    record CallStatusResponse(
            String providerCallId,
            CallStatus status,
            Instant timestamp
    ) {}

    record ProviderStatus(
            TelecomProviderType providerType,
            boolean operational,
            long currentLatencyMs,
            double failureRate,
            String statusMessage
    ) {}
}
