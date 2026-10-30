package com.smartdialer.state;

import com.smartdialer.enums.CallStatus;
import com.smartdialer.exception.IllegalStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class CallStateMachine {

    private final Map<CallStatus, Set<CallStatus>> allowedTransitions = new EnumMap<>(CallStatus.class);

    public CallStateMachine() {
        allowedTransitions.put(CallStatus.QUEUED, EnumSet.of(
                CallStatus.RESERVED,
                CallStatus.CANCELLED
        ));

        allowedTransitions.put(CallStatus.RESERVED, EnumSet.of(
                CallStatus.INITIATED,
                CallStatus.FAILED,
                CallStatus.CANCELLED
        ));

        allowedTransitions.put(CallStatus.INITIATED, EnumSet.of(
                CallStatus.RINGING,
                CallStatus.ANSWERED, // direct answer without ringing notification
                CallStatus.COMPLETED, // provider quick hangup
                CallStatus.FAILED,
                CallStatus.CANCELLED
        ));

        allowedTransitions.put(CallStatus.RINGING, EnumSet.of(
                CallStatus.ANSWERED,
                CallStatus.FAILED,
                CallStatus.CANCELLED,
                CallStatus.COMPLETED // some telecom providers signal hangup directly
        ));

        allowedTransitions.put(CallStatus.ANSWERED, EnumSet.of(
                CallStatus.CONNECTED,
                CallStatus.COMPLETED,
                CallStatus.FAILED,
                CallStatus.CANCELLED // e.g. abandoned call when no agent available
        ));

        allowedTransitions.put(CallStatus.CONNECTED, EnumSet.of(
                CallStatus.COMPLETED,
                CallStatus.FAILED
        ));

        // Terminal states cannot transition to anything
        allowedTransitions.put(CallStatus.COMPLETED, EnumSet.noneOf(CallStatus.class));
        allowedTransitions.put(CallStatus.FAILED, EnumSet.noneOf(CallStatus.class));
        allowedTransitions.put(CallStatus.CANCELLED, EnumSet.noneOf(CallStatus.class));
    }

    public boolean isValidTransition(CallStatus currentStatus, CallStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        if (currentStatus == targetStatus) {
            return true; // Idempotent same-state
        }
        Set<CallStatus> allowed = allowedTransitions.get(currentStatus);
        return allowed != null && allowed.contains(targetStatus);
    }

    public void validateTransition(CallStatus currentStatus, CallStatus targetStatus) {
        if (!isValidTransition(currentStatus, targetStatus)) {
            throw new IllegalStateTransitionException(
                    String.format("Invalid Call state transition from %s to %s", currentStatus, targetStatus)
            );
        }
    }

    public boolean isTerminal(CallStatus status) {
        return status != null && status.isTerminal();
    }

    /**
     * Determines whether an incoming target status is a harmless duplicate or stale/out-of-order event
     * that should be safely ignored rather than throwing an exception.
     */
    public boolean isStaleOrDuplicate(CallStatus currentStatus, CallStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return true;
        }
        if (isTerminal(currentStatus)) {
            return true; // Any non-terminal event arriving after terminal state is ignored
        }
        // Duplicate/late ANSWERED after CONNECTED
        if (targetStatus == CallStatus.ANSWERED && currentStatus == CallStatus.CONNECTED) {
            return true;
        }
        // Stale late RINGING after ANSWERED or CONNECTED
        if (targetStatus == CallStatus.RINGING && 
           (currentStatus == CallStatus.ANSWERED || currentStatus == CallStatus.CONNECTED)) {
            return true;
        }
        // Duplicate/late INITIATED
        if (targetStatus == CallStatus.INITIATED && 
           (currentStatus == CallStatus.RINGING || currentStatus == CallStatus.ANSWERED || currentStatus == CallStatus.CONNECTED)) {
            return true;
        }
        return false;
    }
}
