package com.smartdialer.service.recovery;

import com.smartdialer.entity.CallRecord;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.provider.TelecomProvider;
import com.smartdialer.provider.TelecomProviderRegistry;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.service.agent.AgentService;
import com.smartdialer.service.borrower.BorrowerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private final CallRecordRepository callRecordRepository;
    private final AgentService agentService;
    private final BorrowerService borrowerService;
    private final TelecomProviderRegistry telecomProviderRegistry;

    public RecoveryService(
            CallRecordRepository callRecordRepository,
            AgentService agentService,
            BorrowerService borrowerService,
            TelecomProviderRegistry telecomProviderRegistry
    ) {
        this.callRecordRepository = callRecordRepository;
        this.agentService = agentService;
        this.borrowerService = borrowerService;
        this.telecomProviderRegistry = telecomProviderRegistry;
    }

    /**
     * Recovers stale reservations left in RESERVED state without initiation (e.g. worker crash before dispatch).
     */
    @Transactional
    public int recoverStaleReservations(Duration timeout) {
        List<CallRecord> uninitiated = callRecordRepository.findUninitiatedReservedCalls();
        int recovered = 0;

        for (CallRecord call : uninitiated) {
            log.warn("[Recovery] Found uninitiated reserved call id={}. Recovering resources.", call.getId());
            call.setStatus(CallStatus.CANCELLED);
            call.setFailureReason("RECOVERY_WORKER_CRASH_UNINITIATED");
            call.setCompletedAt(Instant.now());
            callRecordRepository.save(call);

            if (call.getAgentId() != null) {
                agentService.releaseAgent(call.getAgentId());
            }
            borrowerService.releaseReservation(call.getBorrowerId());
            recovered++;
        }
        return recovered;
    }

    /**
     * Inspects in-flight calls with uncertain provider outcomes and reconciles with telecom provider.
     */
    @Transactional
    public int reconcileInFlightCalls(Duration timeout) {
        Instant threshold = Instant.now().minus(timeout);
        List<CallRecord> inFlight = callRecordRepository.findStaleInFlightCalls(threshold);
        int reconciled = 0;

        TelecomProvider activeProvider = telecomProviderRegistry.getActiveProvider();

        for (CallRecord call : inFlight) {
            if (call.getProviderCallId() == null) {
                // Never received providerCallId - cancel safely
                call.setStatus(CallStatus.FAILED);
                call.setFailureReason("RECOVERY_NO_PROVIDER_CONFIRMATION");
                call.setCompletedAt(Instant.now());
                callRecordRepository.save(call);

                if (call.getAgentId() != null) {
                    agentService.releaseAgent(call.getAgentId());
                }
                borrowerService.markFailed(call.getBorrowerId(), 120, 3);
                reconciled++;
                continue;
            }

            try {
                TelecomProvider.CallStatusResponse statusResponse = activeProvider.queryCallStatus(call.getProviderCallId());
                if (statusResponse.status() == CallStatus.COMPLETED || statusResponse.status() == CallStatus.FAILED) {
                    call.setStatus(statusResponse.status());
                    call.setCompletedAt(Instant.now());
                    callRecordRepository.save(call);

                    if (call.getAgentId() != null) {
                        agentService.releaseAgent(call.getAgentId());
                    }
                    borrowerService.markCompleted(call.getBorrowerId());
                    reconciled++;
                    log.info("[Recovery] Reconciled call id={} to {}", call.getId(), statusResponse.status());
                }
            } catch (Exception e) {
                log.error("[Recovery] Error querying provider status for call id={}: {}", call.getId(), e.getMessage());
            }
        }
        return reconciled;
    }

    /**
     * Performs a full recovery pass.
     */
    @Transactional
    public void runFullRecoveryCycle() {
        recoverStaleReservations(Duration.ofSeconds(30));
        reconcileInFlightCalls(Duration.ofSeconds(60));
        agentService.checkAndCleanStaleHeartbeats(Duration.ofSeconds(45));
    }
}
