package com.smartdialer;

import com.smartdialer.entity.Agent;
import com.smartdialer.entity.Borrower;
import com.smartdialer.entity.CallRecord;
import com.smartdialer.entity.OutboxEvent;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.enums.OutboxStatus;
import com.smartdialer.provider.MockProviderA;
import com.smartdialer.provider.TelecomProvider;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.BorrowerRepository;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.repository.OutboxEventRepository;
import com.smartdialer.service.allocation.OutboxDispatcherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ProviderTimeoutAndOutboxTest {

    @Autowired
    private MockProviderA mockProviderA;

    @Autowired
    private OutboxDispatcherService outboxDispatcherService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Test
    @DisplayName("MANDATORY TEST 8: Provider timeout does not create duplicate calls (idempotency key protection)")
    public void testProviderTimeoutDoesNotCreateDuplicateCalls() {
        String idempotencyKey = "IDEMP-TIMEOUT-TEST-" + UUID.randomUUID();

        TelecomProvider.InitiateCallRequest request = new TelecomProvider.InitiateCallRequest(
                101L, 1L, "+15551239999", idempotencyKey
        );

        // First call attempt
        TelecomProvider.InitiateCallResponse response1 = mockProviderA.initiateCall(request);
        assertTrue(response1.success());
        String initialProviderCallId = response1.providerCallId();
        assertNotNull(initialProviderCallId);

        // Repeated retry with the exact same idempotency key (e.g. after network timeout)
        TelecomProvider.InitiateCallResponse response2 = mockProviderA.initiateCall(request);
        assertTrue(response2.success());

        // Assert: Must return the EXACT SAME providerCallId without initiating a duplicate provider leg!
        assertEquals(initialProviderCallId, response2.providerCallId(),
                "Provider must return cached call leg for identical idempotency key");
    }

    @Test
    @DisplayName("MANDATORY TEST 15: Outbox dispatch is idempotent")
    public void testOutboxDispatchIsIdempotent() {
        Agent agent = agentRepository.save(new Agent("Agent-Outbox-" + UUID.randomUUID(), AgentStatus.AVAILABLE, 1L));
        Borrower borrower = borrowerRepository.save(new Borrower("Borrower-Outbox-" + UUID.randomUUID(), "+15559876543", 1L));

        String idempotencyKey = "IDEMP-OUTBOX-" + UUID.randomUUID();
        CallRecord call = new CallRecord(1L, borrower.getId(), agent.getId(), idempotencyKey);
        call.setStatus(CallStatus.RESERVED);
        CallRecord savedCall = callRecordRepository.save(call);

        String payload = String.format(
                "{\"callId\":%d,\"campaignId\":%d,\"borrowerId\":%d,\"agentId\":%d,\"phoneNumber\":\"%s\",\"idempotencyKey\":\"%s\"}",
                savedCall.getId(), 1L, borrower.getId(), agent.getId(), borrower.getPhoneNumber(), idempotencyKey
        );
        OutboxEvent outboxEvent = new OutboxEvent(String.valueOf(savedCall.getId()), "DISPATCH_CALL", payload);
        OutboxEvent savedOutbox = outboxEventRepository.save(outboxEvent);

        // First dispatch execution
        outboxDispatcherService.processSingleDispatch(savedOutbox);

        CallRecord callAfterFirst = callRecordRepository.findById(savedCall.getId()).orElseThrow();
        assertEquals(CallStatus.INITIATED, callAfterFirst.getStatus());
        String firstProviderCallId = callAfterFirst.getProviderCallId();
        assertNotNull(firstProviderCallId);

        OutboxEvent outboxAfterFirst = outboxEventRepository.findById(savedOutbox.getId()).orElseThrow();
        assertEquals(OutboxStatus.COMPLETED, outboxAfterFirst.getStatus());

        // Second redundant dispatch execution on the same event
        outboxDispatcherService.processSingleDispatch(savedOutbox);

        CallRecord callAfterSecond = callRecordRepository.findById(savedCall.getId()).orElseThrow();
        assertEquals(CallStatus.INITIATED, callAfterSecond.getStatus());
        assertEquals(firstProviderCallId, callAfterSecond.getProviderCallId(),
                "Duplicate outbox dispatch must be idempotent and not create duplicate telecom legs");
    }
}
