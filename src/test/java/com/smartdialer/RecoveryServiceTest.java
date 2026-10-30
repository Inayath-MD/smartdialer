package com.smartdialer;

import com.smartdialer.entity.Agent;
import com.smartdialer.entity.Borrower;
import com.smartdialer.entity.CallRecord;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.BorrowerStatus;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.BorrowerRepository;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.service.recovery.RecoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class RecoveryServiceTest {

    @Autowired
    private RecoveryService recoveryService;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Test
    @DisplayName("MANDATORY TEST 12: Stale reservations from simulated worker crash are recovered safely")
    public void testStaleReservationsRecoveredSafely() {
        // Arrange: Worker crashed after reserving Agent & Borrower and creating RESERVED call
        Agent agent = agentRepository.save(new Agent("Agent-CrashedWorker", AgentStatus.RESERVED, 1L));
        Borrower borrower = borrowerRepository.save(new Borrower("Borrower-CrashedWorker", "+15554443333", 1L));
        borrower.setStatus(BorrowerStatus.RESERVED);
        borrowerRepository.save(borrower);

        CallRecord abandonedCall = new CallRecord(1L, borrower.getId(), agent.getId(), "IDEMP-CRASH-" + UUID.randomUUID());
        abandonedCall.setStatus(CallStatus.RESERVED);
        abandonedCall.setInitiatedAt(null); // never initiated due to crash
        CallRecord savedCall = callRecordRepository.save(abandonedCall);

        agent.setCurrentCallId(savedCall.getId());
        agentRepository.save(agent);

        // Act: Run recovery sweep
        int recoveredCount = recoveryService.recoverStaleReservations(Duration.ofSeconds(1));
        assertTrue(recoveredCount >= 1, "Recovery service must recover uninitiated reserved calls");

        // Assert: Call marked CANCELLED
        CallRecord callAfterRecovery = callRecordRepository.findById(savedCall.getId()).orElseThrow();
        assertEquals(CallStatus.CANCELLED, callAfterRecovery.getStatus());
        assertEquals("RECOVERY_WORKER_CRASH_UNINITIATED", callAfterRecovery.getFailureReason());

        // Assert: Agent released safely back to AVAILABLE
        Agent agentAfterRecovery = agentRepository.findById(agent.getId()).orElseThrow();
        assertEquals(AgentStatus.AVAILABLE, agentAfterRecovery.getStatus(), "Agent must be returned to AVAILABLE");
        assertNull(agentAfterRecovery.getCurrentCallId());

        // Assert: Borrower released back to PENDING
        Borrower borrowerAfterRecovery = borrowerRepository.findById(borrower.getId()).orElseThrow();
        assertEquals(BorrowerStatus.PENDING, borrowerAfterRecovery.getStatus(), "Borrower must be returned to PENDING");
    }
}
