package com.smartdialer;

import com.smartdialer.dto.ProviderCallbackEvent;
import com.smartdialer.entity.Agent;
import com.smartdialer.entity.Borrower;
import com.smartdialer.entity.CallRecord;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.enums.ProcessingStatus;
import com.smartdialer.enums.ProviderEventType;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.BorrowerRepository;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.service.dialer.ProviderEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ProviderEventProcessingTest {

    @Autowired
    private ProviderEventService providerEventService;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Test
    @DisplayName("MANDATORY TEST 4: Duplicate ANSWERED events are idempotent")
    public void testDuplicateAnsweredEventsAreIdempotent() {
        CallRecord call = createTestCallWithAgent();
        String providerCallId = call.getProviderCallId();

        // 1st ANSWERED event
        ProviderCallbackEvent event1 = new ProviderCallbackEvent(
                "EVT-ANS-1", providerCallId, ProviderEventType.ANSWERED, Instant.now(), null
        );
        ProcessingStatus status1 = providerEventService.processProviderEvent(event1);
        assertEquals(ProcessingStatus.PROCESSED, status1);

        CallRecord afterFirst = callRecordRepository.findById(call.getId()).orElseThrow();
        assertEquals(CallStatus.CONNECTED, afterFirst.getStatus());

        // 2nd Duplicate ANSWERED event with same providerEventId
        ProcessingStatus status2 = providerEventService.processProviderEvent(event1);
        assertEquals(ProcessingStatus.DUPLICATE, status2, "Duplicate provider event ID must return DUPLICATE");

        // 3rd Duplicate ANSWERED event with different providerEventId
        ProviderCallbackEvent event2 = new ProviderCallbackEvent(
                "EVT-ANS-2", providerCallId, ProviderEventType.ANSWERED, Instant.now(), null
        );
        ProcessingStatus status3 = providerEventService.processProviderEvent(event2);
        assertEquals(ProcessingStatus.PROCESSED, status3);

        CallRecord afterDuplicate = callRecordRepository.findById(call.getId()).orElseThrow();
        assertEquals(CallStatus.CONNECTED, afterDuplicate.getStatus(), "Call status must remain CONNECTED");
    }

    @Test
    @DisplayName("MANDATORY TEST 5: Duplicate COMPLETED events are idempotent")
    public void testDuplicateCompletedEventsAreIdempotent() {
        CallRecord call = createTestCallWithAgent();
        String providerCallId = call.getProviderCallId();

        // Answer and bridge
        providerEventService.processProviderEvent(new ProviderCallbackEvent(
                "EVT-ANS-" + UUID.randomUUID(), providerCallId, ProviderEventType.ANSWERED, Instant.now(), null
        ));

        // First COMPLETED
        String completeEvtId1 = "EVT-COMP-" + UUID.randomUUID();
        ProcessingStatus status1 = providerEventService.processProviderEvent(new ProviderCallbackEvent(
                completeEvtId1, providerCallId, ProviderEventType.COMPLETED, Instant.now(), null
        ));
        assertEquals(ProcessingStatus.PROCESSED, status1);

        CallRecord afterComplete = callRecordRepository.findById(call.getId()).orElseThrow();
        assertEquals(CallStatus.COMPLETED, afterComplete.getStatus());

        // Second COMPLETED
        String completeEvtId2 = "EVT-COMP-" + UUID.randomUUID();
        ProcessingStatus status2 = providerEventService.processProviderEvent(new ProviderCallbackEvent(
                completeEvtId2, providerCallId, ProviderEventType.COMPLETED, Instant.now(), null
        ));
        assertEquals(ProcessingStatus.PROCESSED, status2);

        CallRecord afterDuplicate = callRecordRepository.findById(call.getId()).orElseThrow();
        assertEquals(CallStatus.COMPLETED, afterDuplicate.getStatus());
    }

    @Test
    @DisplayName("MANDATORY TEST 6: COMPLETED followed by ANSWERED does not regress state")
    public void testCompletedFollowedByAnsweredDoesNotRegress() {
        CallRecord call = createTestCallWithAgent();
        String providerCallId = call.getProviderCallId();

        // Complete the call directly
        providerEventService.processProviderEvent(new ProviderCallbackEvent(
                "EVT-COMP-EARLY", providerCallId, ProviderEventType.COMPLETED, Instant.now(), null
        ));

        CallRecord callBeforeLateEvent = callRecordRepository.findById(call.getId()).orElseThrow();
        assertEquals(CallStatus.COMPLETED, callBeforeLateEvent.getStatus());

        // Now a delayed/out-of-order ANSWERED event arrives
        providerEventService.processProviderEvent(new ProviderCallbackEvent(
                "EVT-ANS-LATE", providerCallId, ProviderEventType.ANSWERED, Instant.now(), null
        ));

        // Assert: Call must NOT regress from COMPLETED back to ANSWERED or CONNECTED!
        CallRecord callAfterLateEvent = callRecordRepository.findById(call.getId()).orElseThrow();
        assertEquals(CallStatus.COMPLETED, callAfterLateEvent.getStatus(),
                "Call must remain in terminal COMPLETED state and never regress");
    }

    @Test
    @DisplayName("MANDATORY TEST 7: RINGING arriving after ANSWERED does not regress state")
    public void testRingingArrivingAfterAnsweredDoesNotRegress() {
        CallRecord call = createTestCallWithAgent();
        String providerCallId = call.getProviderCallId();

        // Answer call first
        providerEventService.processProviderEvent(new ProviderCallbackEvent(
                "EVT-ANS-FIRST", providerCallId, ProviderEventType.ANSWERED, Instant.now(), null
        ));

        CallRecord callAfterAnswer = callRecordRepository.findById(call.getId()).orElseThrow();
        assertTrue(callAfterAnswer.getStatus() == CallStatus.ANSWERED || callAfterAnswer.getStatus() == CallStatus.CONNECTED);

        // Delayed RINGING event arrives after answer
        providerEventService.processProviderEvent(new ProviderCallbackEvent(
                "EVT-RING-LATE", providerCallId, ProviderEventType.RINGING, Instant.now(), null
        ));

        CallRecord callAfterLateRinging = callRecordRepository.findById(call.getId()).orElseThrow();
        assertNotEquals(CallStatus.RINGING, callAfterLateRinging.getStatus(),
                "Call must not regress to RINGING after being ANSWERED/CONNECTED");
    }

    @Test
    @DisplayName("MANDATORY TEST 14: Connected calls always have an eligible agent")
    public void testConnectedCallsAlwaysHaveAnEligibleAgent() {
        // Arrange: Predictive call created with NO pre-reserved agent (agentId = null)
        Borrower borrower = borrowerRepository.save(new Borrower("Borrower-NoAgent", "+15559990001", 99L));
        CallRecord unassignedCall = new CallRecord(99L, borrower.getId(), null, "IDEMP-NOAGENT-" + UUID.randomUUID());
        unassignedCall.setStatus(CallStatus.INITIATED);
        unassignedCall.setProviderCallId("PROV-UNASSIGNED-" + UUID.randomUUID());
        CallRecord savedCall = callRecordRepository.save(unassignedCall);

        // Ensure 0 agents are available in campaign 99
        long available = agentRepository.countByCampaignIdAndStatus(99L, AgentStatus.AVAILABLE);
        assertEquals(0, available);

        // Act: Telecom provider signals ANSWERED
        providerEventService.processProviderEvent(new ProviderCallbackEvent(
                "EVT-ANS-ORPHAN", savedCall.getProviderCallId(), ProviderEventType.ANSWERED, Instant.now(), null
        ));

        // Assert: INVARIANT CHECK
        CallRecord callAfterAnswer = callRecordRepository.findById(savedCall.getId()).orElseThrow();
        assertNotEquals(CallStatus.CONNECTED, callAfterAnswer.getStatus(),
                "Call must NEVER be marked CONNECTED without an assigned eligible agent!");
        assertEquals(CallStatus.CANCELLED, callAfterAnswer.getStatus(),
                "Call must be safely CANCELLED/abandoned when no agent is available");
        assertEquals("SAFETY_ABANDONED_NO_ELIGIBLE_AGENT_AVAILABLE", callAfterAnswer.getFailureReason());
    }

    private CallRecord createTestCallWithAgent() {
        Agent agent = agentRepository.save(new Agent("TestAgent-" + UUID.randomUUID(), AgentStatus.AVAILABLE, 1L));
        Borrower borrower = borrowerRepository.save(new Borrower("TestBorrower-" + UUID.randomUUID(), "+1555" + System.nanoTime(), 1L));

        CallRecord call = new CallRecord(1L, borrower.getId(), agent.getId(), "IDEMP-" + UUID.randomUUID());
        call.setStatus(CallStatus.INITIATED);
        call.setProviderCallId("PROV-" + UUID.randomUUID());
        call.setInitiatedAt(Instant.now());
        CallRecord saved = callRecordRepository.save(call);

        agent.setStatus(AgentStatus.DIALING);
        agent.setCurrentCallId(saved.getId());
        agentRepository.save(agent);

        return saved;
    }
}
