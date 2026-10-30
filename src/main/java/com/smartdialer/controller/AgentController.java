package com.smartdialer.controller;

import com.smartdialer.dto.AgentDto;
import com.smartdialer.dto.CreateAgentRequest;
import com.smartdialer.dto.UpdateAgentStatusRequest;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.service.agent.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@Tag(name = "Agents", description = "Agent lifecycle and heartbeat management")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    @Operation(summary = "Register a new collections agent")
    public ResponseEntity<AgentDto> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        AgentDto created = agentService.createAgent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List agents with optional filtering")
    public ResponseEntity<List<AgentDto>> listAgents(
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) AgentStatus status
    ) {
        return ResponseEntity.ok(agentService.listAgents(campaignId, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get agent details by ID")
    public ResponseEntity<AgentDto> getAgent(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.getAgent(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Transition agent state via AgentStateMachine")
    public ResponseEntity<AgentDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAgentStatusRequest request
    ) {
        return ResponseEntity.ok(agentService.updateStatus(id, request.status()));
    }

    @PostMapping("/{id}/heartbeat")
    @Operation(summary = "Record periodic heartbeat for agent availability monitoring")
    public ResponseEntity<Void> heartbeat(@PathVariable Long id) {
        agentService.recordHeartbeat(id);
        return ResponseEntity.noContent().build();
    }
}
