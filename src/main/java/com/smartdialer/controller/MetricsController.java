package com.smartdialer.controller;

import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.repository.CampaignRepository;
import com.smartdialer.repository.SafetyDecisionRecordRepository;
import com.smartdialer.service.dialer.ProviderEventService;
import com.smartdialer.service.recovery.ProviderCircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Metrics & Health", description = "Operational telemetry and system health")
public class MetricsController {

    private final CampaignRepository campaignRepository;
    private final AgentRepository agentRepository;
    private final CallRecordRepository callRecordRepository;
    private final SafetyDecisionRecordRepository safetyDecisionRecordRepository;
    private final ProviderEventService providerEventService;
    private final ProviderCircuitBreaker providerCircuitBreaker;

    public MetricsController(
            CampaignRepository campaignRepository,
            AgentRepository agentRepository,
            CallRecordRepository callRecordRepository,
            SafetyDecisionRecordRepository safetyDecisionRecordRepository,
            ProviderEventService providerEventService,
            ProviderCircuitBreaker providerCircuitBreaker
    ) {
        this.campaignRepository = campaignRepository;
        this.agentRepository = agentRepository;
        this.callRecordRepository = callRecordRepository;
        this.safetyDecisionRecordRepository = safetyDecisionRecordRepository;
        this.providerEventService = providerEventService;
        this.providerCircuitBreaker = providerCircuitBreaker;
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get high-level dialer metrics and telemetry")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(Map.of(
                "totalCampaigns", campaignRepository.count(),
                "totalAgents", agentRepository.count(),
                "availableAgents", agentRepository.countByStatus(AgentStatus.AVAILABLE),
                "engagedAgents", agentRepository.countByStatus(AgentStatus.CONNECTED),
                "totalCalls", callRecordRepository.count(),
                "completedCalls", callRecordRepository.countByCampaignIdAndStatus(1L, CallStatus.COMPLETED),
                "abandonedCalls", providerEventService.getAbandonedCallCount(),
                "totalSafetyDecisions", safetyDecisionRecordRepository.count(),
                "circuitBreakerOpen", providerCircuitBreaker.isOpen(),
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/health")
    @Operation(summary = "System health check")
    public ResponseEntity<Map<String, Object>> getHealth() {
        return ResponseEntity.ok(Map.of(
                "status", providerCircuitBreaker.isOpen() ? "DEGRADED" : "UP",
                "circuitBreaker", providerCircuitBreaker.getState().name(),
                "timestamp", Instant.now().toString()
        ));
    }
}
