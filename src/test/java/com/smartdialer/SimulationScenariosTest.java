package com.smartdialer;

import com.smartdialer.dto.SimulationResultDto;
import com.smartdialer.dto.SimulationRunRequest;
import com.smartdialer.enums.DialingMode;
import com.smartdialer.service.simulation.SimulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class SimulationScenariosTest {

    private static final Logger log = LoggerFactory.getLogger(SimulationScenariosTest.class);

    @Autowired
    private SimulationService simulationService;

    @Test
    @DisplayName("SCENARIO A: Low Answer Rate (20%), Avg Talk Time 120s - Progressive vs Predictive")
    public void testScenarioA() {
        // Run Progressive
        SimulationRunRequest reqProg = new SimulationRunRequest(
                10, 50, 0.20, 120, 30, 0.02, DialingMode.PROGRESSIVE, 10, false, "Scenario A - Progressive"
        );
        SimulationResultDto resProg = simulationService.runSimulation(reqProg);

        // Run Predictive
        SimulationRunRequest reqPred = new SimulationRunRequest(
                10, 50, 0.20, 120, 30, 0.02, DialingMode.PREDICTIVE, 10, false, "Scenario A - Predictive"
        );
        SimulationResultDto resPred = simulationService.runSimulation(reqPred);

        assertNotNull(resProg);
        assertNotNull(resPred);
        assertTrue(resProg.zeroOrphanInvariantPreserved(), "Invariant must be preserved in Progressive mode");
        assertTrue(resPred.zeroOrphanInvariantPreserved(), "Invariant must be preserved in Predictive mode");

        log.info("Scenario A Results: Progressive Init={}, Conn={}, Util={}% | Predictive Init={}, Conn={}, Util={}%",
                resProg.totalCallsInitiated(), resProg.totalConnectedCalls(), resProg.agentUtilizationPercentage(),
                resPred.totalCallsInitiated(), resPred.totalConnectedCalls(), resPred.agentUtilizationPercentage());
    }

    @Test
    @DisplayName("SCENARIO B: Medium Answer Rate (50%), Avg Talk Time 90s - Progressive vs Predictive")
    public void testScenarioB() {
        SimulationRunRequest reqProg = new SimulationRunRequest(
                15, 60, 0.50, 90, 40, 0.03, DialingMode.PROGRESSIVE, 10, false, "Scenario B - Progressive"
        );
        SimulationResultDto resProg = simulationService.runSimulation(reqProg);

        SimulationRunRequest reqPred = new SimulationRunRequest(
                15, 60, 0.50, 90, 40, 0.03, DialingMode.PREDICTIVE, 10, false, "Scenario B - Predictive"
        );
        SimulationResultDto resPred = simulationService.runSimulation(reqPred);

        assertTrue(resProg.zeroOrphanInvariantPreserved());
        assertTrue(resPred.zeroOrphanInvariantPreserved());

        log.info("Scenario B Results: Progressive Util={}% | Predictive Util={}%",
                resProg.agentUtilizationPercentage(), resPred.agentUtilizationPercentage());
    }

    @Test
    @DisplayName("SCENARIO C: High Answer Rate (70%), Avg Talk Time 180s - Progressive vs Predictive")
    public void testScenarioC() {
        SimulationRunRequest reqProg = new SimulationRunRequest(
                20, 80, 0.70, 180, 25, 0.01, DialingMode.PROGRESSIVE, 10, false, "Scenario C - Progressive"
        );
        SimulationResultDto resProg = simulationService.runSimulation(reqProg);

        SimulationRunRequest reqPred = new SimulationRunRequest(
                20, 80, 0.70, 180, 25, 0.01, DialingMode.PREDICTIVE, 10, false, "Scenario C - Predictive"
        );
        SimulationResultDto resPred = simulationService.runSimulation(reqPred);

        assertTrue(resProg.zeroOrphanInvariantPreserved());
        assertTrue(resPred.zeroOrphanInvariantPreserved());

        log.info("Scenario C Results: Progressive Util={}% | Predictive Util={}%",
                resProg.agentUtilizationPercentage(), resPred.agentUtilizationPercentage());
    }

    @Test
    @DisplayName("SCENARIO D: Dynamically Changing Answer Rate")
    public void testScenarioD() {
        SimulationRunRequest reqDynamic = new SimulationRunRequest(
                15, 100, 0.40, 100, 35, 0.05, DialingMode.PREDICTIVE, 14, true, "Scenario D - Dynamic Answer Rate"
        );
        SimulationResultDto resDynamic = simulationService.runSimulation(reqDynamic);

        assertNotNull(resDynamic);
        assertTrue(resDynamic.zeroOrphanInvariantPreserved(), "Invariant must be preserved even under dynamic answer rates");
        assertTrue(resDynamic.totalCallsInitiated() > 0);

        log.info("Scenario D Dynamic Results: Initiated={}, Connected={}, Fallbacks={}, InvariantPreserved={}",
                resDynamic.totalCallsInitiated(), resDynamic.totalConnectedCalls(),
                resDynamic.progressiveFallbackCount(), resDynamic.zeroOrphanInvariantPreserved());
    }
}
