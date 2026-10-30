package com.smartdialer.controller;

import com.smartdialer.dto.CampaignDto;
import com.smartdialer.dto.CreateCampaignRequest;
import com.smartdialer.service.campaign.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
@Tag(name = "Campaigns", description = "Campaign management endpoints")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    @Operation(summary = "Create a new campaign")
    public ResponseEntity<CampaignDto> createCampaign(@Valid @RequestBody CreateCampaignRequest request) {
        CampaignDto created = campaignService.createCampaign(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List all campaigns")
    public ResponseEntity<List<CampaignDto>> listCampaigns() {
        return ResponseEntity.ok(campaignService.listCampaigns());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get campaign details by ID")
    public ResponseEntity<CampaignDto> getCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaign(id));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Activate/start a campaign")
    public ResponseEntity<CampaignDto> startCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.startCampaign(id));
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause an active campaign")
    public ResponseEntity<CampaignDto> pauseCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.pauseCampaign(id));
    }
}
