package com.smartdialer.controller;

import com.smartdialer.dto.ProviderCallbackEvent;
import com.smartdialer.dto.ProviderHealthDto;
import com.smartdialer.enums.ProcessingStatus;
import com.smartdialer.enums.TelecomProviderType;
import com.smartdialer.provider.TelecomProvider;
import com.smartdialer.provider.TelecomProviderRegistry;
import com.smartdialer.service.dialer.ProviderEventService;
import com.smartdialer.service.recovery.ProviderCircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/providers")
@Tag(name = "Telecom Providers", description = "Provider webhooks and health inspection")
public class ProviderController {

    private final ProviderEventService providerEventService;
    private final TelecomProviderRegistry telecomProviderRegistry;
    private final ProviderCircuitBreaker providerCircuitBreaker;

    public ProviderController(
            ProviderEventService providerEventService,
            TelecomProviderRegistry telecomProviderRegistry,
            ProviderCircuitBreaker providerCircuitBreaker
    ) {
        this.providerEventService = providerEventService;
        this.telecomProviderRegistry = telecomProviderRegistry;
        this.providerCircuitBreaker = providerCircuitBreaker;
    }

    @PostMapping("/{provider}/events")
    @Operation(summary = "Idempotent telecom webhook endpoint for call events")
    public ResponseEntity<Map<String, Object>> handleProviderEvent(
            @PathVariable String provider,
            @Valid @RequestBody ProviderCallbackEvent event
    ) {
        ProcessingStatus status = providerEventService.processProviderEvent(event);
        return ResponseEntity.ok(Map.of(
                "provider", provider,
                "providerEventId", event.providerEventId(),
                "status", status
        ));
    }

    @GetMapping("/health")
    @Operation(summary = "Check health metrics and circuit breaker status for telecom providers")
    public ResponseEntity<List<ProviderHealthDto>> getProviderHealth() {
        List<ProviderHealthDto> healthList = new ArrayList<>();
        Map<TelecomProviderType, TelecomProvider> all = telecomProviderRegistry.getAllProviders();

        for (Map.Entry<TelecomProviderType, TelecomProvider> entry : all.entrySet()) {
            TelecomProvider.ProviderStatus status = entry.getValue().getProviderStatus();
            healthList.add(new ProviderHealthDto(
                    entry.getKey().name(),
                    status.operational() && !providerCircuitBreaker.isOpen(),
                    status.failureRate(),
                    status.currentLatencyMs(),
                    providerCircuitBreaker.isOpen(),
                    providerCircuitBreaker.getConsecutiveFailures()
            ));
        }
        return ResponseEntity.ok(healthList);
    }
}
