package com.smartdialer.provider;

import com.smartdialer.enums.CallStatus;
import com.smartdialer.enums.TelecomProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MockProviderA implements TelecomProvider {

    private static final Logger log = LoggerFactory.getLogger(MockProviderA.class);

    private final Map<String, InitiateCallResponse> idempotencyMap = new ConcurrentHashMap<>();
    private final Map<String, CallStatus> callStatusMap = new ConcurrentHashMap<>();
    
    private volatile double failureRate = 0.01; // 1% failure
    private volatile long baseLatencyMs = 25; // 25ms
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);

    @Override
    public TelecomProviderType getProviderType() {
        return TelecomProviderType.MOCK_A;
    }

    @Override
    public InitiateCallResponse initiateCall(InitiateCallRequest request) {
        // Enforce provider-level idempotency
        if (request.idempotencyKey() != null && idempotencyMap.containsKey(request.idempotencyKey())) {
            log.info("[MockProviderA] Idempotent hit for key: {}", request.idempotencyKey());
            return idempotencyMap.get(request.idempotencyKey());
        }

        totalCalls.incrementAndGet();

        // Simulate network latency
        simulateLatency(baseLatencyMs);

        // Check simulated failure
        if (Math.random() < failureRate) {
            failedCalls.incrementAndGet();
            InitiateCallResponse failedResponse = new InitiateCallResponse(
                    false,
                    null,
                    "TELCO_REJECTED",
                    "Simulated telecom initiation rejection"
            );
            if (request.idempotencyKey() != null) {
                idempotencyMap.put(request.idempotencyKey(), failedResponse);
            }
            return failedResponse;
        }

        String providerCallId = "PROV-A-" + UUID.randomUUID();
        callStatusMap.put(providerCallId, CallStatus.INITIATED);

        InitiateCallResponse successResponse = new InitiateCallResponse(
                true,
                providerCallId,
                null,
                null
        );

        if (request.idempotencyKey() != null) {
            idempotencyMap.put(request.idempotencyKey(), successResponse);
        }

        log.debug("[MockProviderA] Successfully initiated call {} with providerCallId: {}", request.callId(), providerCallId);
        return successResponse;
    }

    @Override
    public ProviderStatus getProviderStatus() {
        long total = totalCalls.get();
        double currentFailureRate = total == 0 ? this.failureRate : (double) failedCalls.get() / total;
        double effectiveFailureRate = Math.max(this.failureRate, currentFailureRate);
        boolean operational = effectiveFailureRate < 0.25;
        return new ProviderStatus(
                TelecomProviderType.MOCK_A,
                operational,
                baseLatencyMs,
                effectiveFailureRate,
                operational ? "Healthy fast mock provider" : "Degraded failure rate"
        );
    }

    @Override
    public CallStatusResponse queryCallStatus(String providerCallId) {
        simulateLatency(10);
        CallStatus status = callStatusMap.getOrDefault(providerCallId, CallStatus.INITIATED);
        return new CallStatusResponse(providerCallId, status, Instant.now());
    }

    public void updateCallStatus(String providerCallId, CallStatus status) {
        callStatusMap.put(providerCallId, status);
    }

    @Override
    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public void setBaseLatencyMs(long latencyMs) {
        this.baseLatencyMs = latencyMs;
    }

    private void simulateLatency(long ms) {
        if (ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
