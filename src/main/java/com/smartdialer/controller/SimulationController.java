package com.smartdialer.controller;

import com.smartdialer.dto.SimulationResultDto;
import com.smartdialer.dto.SimulationRunRequest;
import com.smartdialer.exception.ResourceNotFoundException;
import com.smartdialer.service.simulation.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation")
@Tag(name = "Simulation Engine", description = "Run progressive and predictive dialed scenarios and retrieve metrics")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/run")
    @Operation(summary = "Execute a full simulation scenario (Scenarios A, B, C, D)")
    public ResponseEntity<SimulationResultDto> runSimulation(@Valid @RequestBody SimulationRunRequest request) {
        SimulationResultDto result = simulationService.runSimulation(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/results/{id}")
    @Operation(summary = "Retrieve results of a completed simulation run")
    public ResponseEntity<SimulationResultDto> getResults(@PathVariable String id) {
        SimulationResultDto result = simulationService.getSimulationResult(id);
        if (result == null) {
            throw new ResourceNotFoundException("Simulation result not found for id: " + id);
        }
        return ResponseEntity.ok(result);
    }
}
