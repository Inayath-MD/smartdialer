package com.smartdialer.provider;

import com.smartdialer.enums.CallStatus;
import com.smartdialer.enums.TelecomProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MockProviderB implements TelecomProvider {

    private static final Logger log = LoggerFactory.getLogger(MockProviderB.class);
    private final Random random = new Random();

    private final Map<String, InitiateCallResponse> idempotencyMap = new ConcurrentHashMap<>();
    private final Map<String, CallStatus> callStatusMap = new ConcurrentHashMap<>();

    private volatile double failureRate = 0.15; // 15% failure
    private volatile long baseLatencyMs = 200; // 200ms
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);

    @Override
    public TelecomProviderType getProviderType() {
        return TelecomProviderType.MOCK_B;
    }

    @Override
    public InitiateCallResponse initiateCall(InitiateCallRequest request) {
        // Enforce provider-level idempotency
        if (request.idempotencyKey() != null && idempotencyMap.containsKey(request.idempotencyKey())) {
            log.info("[MockProviderB] Idempotent hit for key: {}", request.idempotencyKey());
            return idempotencyMap.get(request.idempotencyKey());
        }

        totalCalls.incrementAndGet();

        // Simulate erratic network latency (baseLatency +/- 50%)
        long jitter = (long) (baseLatencyMs * (0.8 + 0.4 * random.nextDouble()));
        simulateLatency(jitter);

        // Check simulated failure or timeout
        if (random.nextDouble() < failureRate) {
            failedCalls.incrementAndGet();
            String errorCode = random.nextBoolean() ? "TELCO_TIMEOUT" : "CIRCUIT_BUSY";
            InitiateCallResponse failedResponse = new InitiateCallResponse(
                    false,
                    null,
                    errorCode,
                    "Simulated telecom failure in MockProviderB: " + errorCode
            );
            if (request.idempotencyKey() != null) {
                idempotencyMap.put(request.idempotencyKey(), failedResponse);
            }
            log.warn("[MockProviderB] Call {} failed with {}", request.callId(), errorCode);
            return failedResponse;
        }

        String providerCallId = "PROV-B-" + UUID.randomUUID();
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

        log.debug("[MockProviderB] Call {} initiated with providerCallId: {}", request.callId(), providerCallId);
        return successResponse;
    }

    @Override
    public ProviderStatus getProviderStatus() {
        long total = totalCalls.get();
        double currentFailureRate = total == 0 ? this.failureRate : (double) failedCalls.get() / total;
        double effectiveFailureRate = Math.max(this.failureRate, currentFailureRate);
        boolean operational = effectiveFailureRate < 0.25;
        return new ProviderStatus(
                TelecomProviderType.MOCK_B,
                operational,
                baseLatencyMs,
                effectiveFailureRate,
                operational ? "Degraded/High Latency Mock Provider" : "High Failure Rate Provider"
        );
    }

    @Override
    public CallStatusResponse queryCallStatus(String providerCallId) {
        simulateLatency(50);
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
