package com.smartdialer.scheduler;

import com.smartdialer.config.DialerProperties;
import com.smartdialer.service.agent.AgentService;
import com.smartdialer.service.allocation.OutboxDispatcherService;
import com.smartdialer.service.recovery.RecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "smartdialer", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class DialerScheduler {

    private static final Logger log = LoggerFactory.getLogger(DialerScheduler.class);

    private final OutboxDispatcherService outboxDispatcherService;
    private final RecoveryService recoveryService;
    private final AgentService agentService;
    private final DialerProperties dialerProperties;

    public DialerScheduler(
            OutboxDispatcherService outboxDispatcherService,
            RecoveryService recoveryService,
            AgentService agentService,
            DialerProperties dialerProperties
    ) {
        this.outboxDispatcherService = outboxDispatcherService;
        this.recoveryService = recoveryService;
        this.agentService = agentService;
        this.dialerProperties = dialerProperties;
    }

    @Scheduled(fixedDelay = 1000)
    public void processOutbox() {
        try {
            int dispatched = outboxDispatcherService.processPendingDispatches();
            if (dispatched > 0) {
                log.debug("Scheduler processed {} outbox dispatches", dispatched);
            }
        } catch (Exception e) {
            log.error("Scheduler outbox dispatch error: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void runRecovery() {
        try {
            recoveryService.runFullRecoveryCycle();
        } catch (Exception e) {
            log.error("Scheduler recovery error: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void checkHeartbeats() {
        try {
            int stale = agentService.checkAndCleanStaleHeartbeats(
                    Duration.ofSeconds(dialerProperties.getHeartbeatTimeoutSeconds())
            );
            if (stale > 0) {
                log.warn("Scheduler cleaned {} stale agent heartbeats", stale);
            }
        } catch (Exception e) {
            log.error("Scheduler heartbeat sweep error: {}", e.getMessage(), e);
        }
    }
}
