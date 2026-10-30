package com.smartdialer.state;

import com.smartdialer.enums.AgentStatus;
import com.smartdialer.exception.IllegalStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class AgentStateMachine {

    private final Map<AgentStatus, Set<AgentStatus>> allowedTransitions = new EnumMap<>(AgentStatus.class);

    public AgentStateMachine() {
        allowedTransitions.put(AgentStatus.OFFLINE, EnumSet.of(AgentStatus.AVAILABLE));
        
        allowedTransitions.put(AgentStatus.AVAILABLE, EnumSet.of(
                AgentStatus.RESERVED,
                AgentStatus.DIALING,
                AgentStatus.CONNECTED, // immediate bridge in predictive mode
                AgentStatus.PAUSED,
                AgentStatus.OFFLINE
        ));
        
        allowedTransitions.put(AgentStatus.RESERVED, EnumSet.of(
                AgentStatus.DIALING,
                AgentStatus.CONNECTED, // bridged on borrower answer
                AgentStatus.AVAILABLE, // allocation rollback or timeout
                AgentStatus.OFFLINE
        ));
        
        allowedTransitions.put(AgentStatus.DIALING, EnumSet.of(
                AgentStatus.CONNECTED,
                AgentStatus.AVAILABLE, // call unanswered or failed before bridge
                AgentStatus.WRAP_UP,
                AgentStatus.OFFLINE
        ));
        
        allowedTransitions.put(AgentStatus.CONNECTED, EnumSet.of(
                AgentStatus.WRAP_UP,
                AgentStatus.AVAILABLE,
                AgentStatus.OFFLINE
        ));
        
        allowedTransitions.put(AgentStatus.WRAP_UP, EnumSet.of(
                AgentStatus.AVAILABLE,
                AgentStatus.PAUSED,
                AgentStatus.OFFLINE
        ));
        
        allowedTransitions.put(AgentStatus.PAUSED, EnumSet.of(
                AgentStatus.AVAILABLE,
                AgentStatus.OFFLINE
        ));
    }

    public boolean isValidTransition(AgentStatus currentStatus, AgentStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        if (currentStatus == targetStatus) {
            return true; // idempotent
        }
        Set<AgentStatus> targets = allowedTransitions.get(currentStatus);
        return targets != null && targets.contains(targetStatus);
    }

    public void validateTransition(AgentStatus currentStatus, AgentStatus targetStatus) {
        if (!isValidTransition(currentStatus, targetStatus)) {
            throw new IllegalStateTransitionException(
                    String.format("Invalid Agent transition from %s to %s", currentStatus, targetStatus)
            );
        }
    }

    public boolean isAvailableForCall(AgentStatus status) {
        return status == AgentStatus.AVAILABLE;
    }

    public boolean isEngaged(AgentStatus status) {
        return status == AgentStatus.RESERVED || status == AgentStatus.DIALING || status == AgentStatus.CONNECTED;
    }
}
