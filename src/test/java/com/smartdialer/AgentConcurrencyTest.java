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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AgentConcurrencyTest {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentRepository agentRepository;

    @Test
    @DisplayName("MANDATORY TEST 1: Two workers cannot reserve the same agent simultaneously")
    public void testTwoWorkersCannotReserveSameAgent() throws InterruptedException, ExecutionException {
        // Arrange
        Agent agent = new Agent("Agent-Concurrency-Test", AgentStatus.AVAILABLE, 1L);
        Agent savedAgent = agentRepository.save(agent);
        Long agentId = savedAgent.getId();

        int numberOfWorkers = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfWorkers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numberOfWorkers);
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failureCounter = new AtomicInteger(0);

        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 1; i <= numberOfWorkers; i++) {
            final long callId = 1000L + i;
            futures.add(executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize all workers to strike simultaneously
                    boolean reserved = agentService.reserveAgent(agentId, callId);
                    if (reserved) {
                        successCounter.incrementAndGet();
                        return true;
                    } else {
                        failureCounter.incrementAndGet();
                        return false;
                    }
                } finally {
                    endGate.countDown();
                }
            }));
        }

        // Fire all workers simultaneously
        startGate.countDown();
        endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert: Exactly ONE worker successfully reserved the agent
        assertEquals(1, successCounter.get(), "Exactly one worker must succeed in reserving the agent");
        assertEquals(numberOfWorkers - 1, failureCounter.get(), "All other concurrent workers must fail to reserve");

        Agent postReservation = agentRepository.findById(agentId).orElseThrow();
        assertEquals(AgentStatus.RESERVED, postReservation.getStatus(), "Agent status must be RESERVED");
        assertNotNull(postReservation.getCurrentCallId(), "Agent must have an assigned callId");
    }
}
