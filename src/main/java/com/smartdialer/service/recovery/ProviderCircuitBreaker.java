package com.smartdialer.service.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ProviderCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(ProviderCircuitBreaker.class);

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastStateChangeTime = new AtomicLong(System.currentTimeMillis());

    private final int failureThreshold = 5; // 5 consecutive failures trips the breaker
    private final long openDurationMs = 15000; // 15 seconds cool-off

    public boolean isOpen() {
        State current = state.get();
        if (current == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastStateChangeTime.get();
            if (elapsed >= openDurationMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("[CircuitBreaker] Transitioned from OPEN to HALF_OPEN (trial state)");
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        if (state.get() == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                lastStateChangeTime.set(System.currentTimeMillis());
                log.info("[CircuitBreaker] Provider recovered. Breaker returned to CLOSED.");
            }
        }
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN) || state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                lastStateChangeTime.set(System.currentTimeMillis());
                log.warn("[CircuitBreaker] Failure threshold ({}) breached! Breaker is now OPEN.", failures);
            }
        }
    }

    public State getState() {
        return state.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void reset() {
        state.set(State.CLOSED);
        consecutiveFailures.set(0);
        lastStateChangeTime.set(System.currentTimeMillis());
    }
}
