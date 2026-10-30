package com.smartdialer.controller;

import com.smartdialer.dto.DialerRunRequest;
import com.smartdialer.dto.DialerRunResponse;
import com.smartdialer.dto.DialerStatusResponse;
import com.smartdialer.entity.Campaign;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CallStatus;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.repository.CampaignRepository;
import com.smartdialer.service.borrower.BorrowerService;
import com.smartdialer.service.dialer.PredictiveDialerService;
import com.smartdialer.service.dialer.ProgressiveDialerService;
import com.smartdialer.service.recovery.ProviderCircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dialer")
@Tag(name = "Dialer Engine", description = "Progressive and Predictive outbound dialing execution")
public class DialerController {

    private final ProgressiveDialerService progressiveDialerService;
    private final PredictiveDialerService predictiveDialerService;
    private final CampaignRepository campaignRepository;
    private final AgentRepository agentRepository;
    private final CallRecordRepository callRecordRepository;
    private final BorrowerService borrowerService;
    private final ProviderCircuitBreaker providerCircuitBreaker;

    public DialerController(
            ProgressiveDialerService progressiveDialerService,
            PredictiveDialerService predictiveDialerService,
            CampaignRepository campaignRepository,
            AgentRepository agentRepository,
            CallRecordRepository callRecordRepository,
            BorrowerService borrowerService,
            ProviderCircuitBreaker providerCircuitBreaker
    ) {
        this.progressiveDialerService = progressiveDialerService;
        this.predictiveDialerService = predictiveDialerService;
        this.campaignRepository = campaignRepository;
        this.agentRepository = agentRepository;
        this.callRecordRepository = callRecordRepository;
        this.borrowerService = borrowerService;
        this.providerCircuitBreaker = providerCircuitBreaker;
    }

    @PostMapping("/progressive/run")
    @Operation(summary = "Execute a progressive dialing batch for a campaign")
    public ResponseEntity<DialerRunResponse> runProgressive(@Valid @RequestBody DialerRunRequest request) {
        DialerRunResponse response = progressiveDialerService.runProgressiveDialing(request.campaignId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/predictive/run")
    @Operation(summary = "Execute a predictive dialing batch with safety authorization")
    public ResponseEntity<DialerRunResponse> runPredictive(@Valid @RequestBody DialerRunRequest request) {
        DialerRunResponse response = predictiveDialerService.runPredictiveDialing(request.campaignId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    @Operation(summary = "Get real-time operational status for a campaign")
    public ResponseEntity<DialerStatusResponse> getStatus(@RequestParam Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow();

        long available = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.AVAILABLE);
        long reserved = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.RESERVED);
        long dialing = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.DIALING);
        long connected = agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.CONNECTED);

        long ringing = callRecordRepository.countByCampaignIdAndStatus(campaignId, CallStatus.RINGING);
        long connectedCalls = callRecordRepository.countByCampaignIdAndStatus(campaignId, CallStatus.CONNECTED);

        long eligibleBorrowers = borrowerService.countEligibleBorrowers(campaignId, campaign.getMaxAttemptsPerBorrower());

        return ResponseEntity.ok(new DialerStatusResponse(
                campaignId,
                campaign.getStatus(),
                campaign.getDialingMode(),
                available,
                reserved,
                dialing,
                connected,
                ringing,
                connectedCalls,
                eligibleBorrowers,
                providerCircuitBreaker.isOpen()
        ));
    }
}
