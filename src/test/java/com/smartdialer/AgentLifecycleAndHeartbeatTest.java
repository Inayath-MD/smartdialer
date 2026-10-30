package com.smartdialer;

import com.smartdialer.entity.Agent;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.service.agent.AgentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AgentLifecycleAndHeartbeatTest {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentRepository agentRepository;

    @Test
    @DisplayName("MANDATORY TEST 9: Agent becoming OFFLINE during allocation is handled safely")
    public void testAgentBecomingOfflineHandledSafely() {
        Agent agent = agentRepository.save(new Agent("Agent-Offline-Test", AgentStatus.AVAILABLE, 1L));

        // Agent abruptly transitions to OFFLINE
        agentService.updateStatus(agent.getId(), AgentStatus.OFFLINE);

        Agent updated = agentRepository.findById(agent.getId()).orElseThrow();
        assertEquals(AgentStatus.OFFLINE, updated.getStatus());

        // Attempting to reserve an OFFLINE agent must fail atomically
        boolean reserved = agentService.reserveAgent(agent.getId(), 501L);
        assertFalse(reserved, "Reservation of an OFFLINE agent must be rejected");

        // Verify status remains OFFLINE and currentCallId is not set
        Agent postAttempt = agentRepository.findById(agent.getId()).orElseThrow();
        assertEquals(AgentStatus.OFFLINE, postAttempt.getStatus());
        assertNull(postAttempt.getCurrentCallId());
    }

    @Test
    @DisplayName("Agent heartbeat timeout transitions stale agent to OFFLINE")
    public void testHeartbeatTimeoutTransitionsToOffline() {
        Agent agent = new Agent("Agent-Heartbeat-Timeout", AgentStatus.AVAILABLE, 1L);
        // Set last heartbeat to 2 minutes ago
        agent.setLastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(2)));
        Agent saved = agentRepository.save(agent);

        int cleaned = agentService.checkAndCleanStaleHeartbeats(Duration.ofSeconds(30));
        assertTrue(cleaned >= 1);

        Agent cleanedAgent = agentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(AgentStatus.OFFLINE, cleanedAgent.getStatus(), "Stale agent must transition to OFFLINE");
    }
}
