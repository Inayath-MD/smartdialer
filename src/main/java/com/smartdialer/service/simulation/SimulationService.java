package com.smartdialer.service.simulation;

import com.smartdialer.dto.DialerRunResponse;
import com.smartdialer.dto.ProviderCallbackEvent;
import com.smartdialer.dto.SimulationResultDto;
import com.smartdialer.dto.SimulationRunRequest;
import com.smartdialer.entity.Agent;
import com.smartdialer.entity.Borrower;
import com.smartdialer.entity.CallRecord;
import com.smartdialer.entity.Campaign;
import com.smartdialer.enums.*;
import com.smartdialer.provider.MockProviderA;
import com.smartdialer.provider.MockProviderB;
import com.smartdialer.provider.TelecomProviderRegistry;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.BorrowerRepository;
import com.smartdialer.repository.CallRecordRepository;
import com.smartdialer.repository.CampaignRepository;
import com.smartdialer.repository.SafetyDecisionRecordRepository;
import com.smartdialer.service.agent.AgentService;
import com.smartdialer.service.dialer.PredictiveDialerService;
import com.smartdialer.service.dialer.ProgressiveDialerService;
import com.smartdialer.service.dialer.ProviderEventService;
import com.smartdialer.service.pacing.PredictivePacingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final CampaignRepository campaignRepository;
    private final AgentRepository agentRepository;
    private final BorrowerRepository borrowerRepository;
    private final CallRecordRepository callRecordRepository;
    private final SafetyDecisionRecordRepository safetyDecisionRecordRepository;
    private final ProgressiveDialerService progressiveDialerService;
    private final PredictiveDialerService predictiveDialerService;
    private final ProviderEventService providerEventService;
    private final PredictivePacingService predictivePacingService;
    private final TelecomProviderRegistry telecomProviderRegistry;
    private final AgentService agentService;
    private final MockProviderA mockProviderA;
    private final MockProviderB mockProviderB;

    private final Map<String, SimulationResultDto> resultsCache = new ConcurrentHashMap<>();

    public SimulationService(
            CampaignRepository campaignRepository,
            AgentRepository agentRepository,
            BorrowerRepository borrowerRepository,
            CallRecordRepository callRecordRepository,
            SafetyDecisionRecordRepository safetyDecisionRecordRepository,
            ProgressiveDialerService progressiveDialerService,
            PredictiveDialerService predictiveDialerService,
            ProviderEventService providerEventService,
            PredictivePacingService predictivePacingService,
            TelecomProviderRegistry telecomProviderRegistry,
            AgentService agentService,
            MockProviderA mockProviderA,
            MockProviderB mockProviderB
    ) {
        this.campaignRepository = campaignRepository;
        this.agentRepository = agentRepository;
        this.borrowerRepository = borrowerRepository;
        this.callRecordRepository = callRecordRepository;
        this.safetyDecisionRecordRepository = safetyDecisionRecordRepository;
        this.progressiveDialerService = progressiveDialerService;
        this.predictiveDialerService = predictiveDialerService;
        this.providerEventService = providerEventService;
        this.predictivePacingService = predictivePacingService;
        this.telecomProviderRegistry = telecomProviderRegistry;
        this.agentService = agentService;
        this.mockProviderA = mockProviderA;
        this.mockProviderB = mockProviderB;
    }

    public SimulationResultDto runSimulation(SimulationRunRequest request) {
        String simId = UUID.randomUUID().toString();
        log.info("Starting simulation id={} scenario='{}' mode={} agents={} borrowers={}",
                simId, request.scenarioName(), request.dialingMode(), request.numberOfAgents(), request.numberOfBorrowers());

        long startTime = System.currentTimeMillis();

        // 1. Configure Mock Provider settings
        mockProviderA.setBaseLatencyMs(request.providerLatencyMs());
        mockProviderA.setFailureRate(request.providerFailureRate());
        mockProviderB.setBaseLatencyMs(Math.max(100, request.providerLatencyMs() * 2));
        mockProviderB.setFailureRate(Math.max(0.15, request.providerFailureRate() * 1.5));

        // 2. Setup Campaign and seed database records
        Campaign campaign = setupSimulationCampaign(request, simId);
        Long campaignId = campaign.getId();

        predictivePacingService.updateStats(campaignId, request.answerRate(), request.averageTalkTimeSeconds());

        // 3. Execution metrics accumulators
        AtomicInteger initiatedCount = new AtomicInteger(0);
        AtomicInteger answeredCount = new AtomicInteger(0);
        AtomicInteger connectedCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        AtomicInteger providerFailures = new AtomicInteger(0);

        AtomicInteger safetyApprovals = new AtomicInteger(0);
        AtomicInteger safetyReductions = new AtomicInteger(0);
        AtomicInteger safetyRejections = new AtomicInteger(0);
        AtomicInteger fallbacks = new AtomicInteger(0);

        List<String> invariantViolations = new ArrayList<>();
        Random random = new Random(42); // deterministic seed for reproducibility

        // Simulation parameters
        int maxRounds = Math.max(10, request.simulationDurationSeconds() / 2);
        double currentAnswerRate = request.answerRate();
        double totalTalkTimeAgentSeconds = 0;

        // 4. Run discrete simulation loop
        for (int round = 1; round <= maxRounds; round++) {
            // Dynamic answer rate variation for Scenario D
            if (request.dynamicAnswerRate()) {
                if (round % 3 == 0) {
                    currentAnswerRate = 0.20; // sudden drop
                } else if (round % 5 == 0) {
                    currentAnswerRate = 0.80; // surge
                } else {
                    currentAnswerRate = 0.50; // normal
                }
                predictivePacingService.updateStats(campaignId, currentAnswerRate, request.averageTalkTimeSeconds());
            }

            // Step A: Trigger Dialer
            DialerRunResponse runResponse;
            if (request.dialingMode() == DialingMode.PROGRESSIVE) {
                runResponse = progressiveDialerService.runProgressiveDialing(campaignId);
            } else {
                runResponse = predictiveDialerService.runPredictiveDialing(campaignId);
            }

            // Record safety outcomes
            switch (runResponse.decision()) {
                case APPROVE -> safetyApprovals.incrementAndGet();
                case REDUCE -> safetyReductions.incrementAndGet();
                case REJECT -> safetyRejections.incrementAndGet();
                case PROGRESSIVE_FALLBACK -> fallbacks.incrementAndGet();
            }

            initiatedCount.addAndGet(runResponse.allocatedCalls());

            // Step B: Simulate provider events for in-flight initiated calls
            List<CallRecord> initiatedCalls = callRecordRepository.findByCampaignId(
                    campaignId,
                    org.springframework.data.domain.PageRequest.of(0, 50)
            ).stream().filter(c -> c.getStatus() == CallStatus.INITIATED).toList();

            for (CallRecord call : initiatedCalls) {
                // Event 1: RINGING
                String ringEventId = "EVT-RING-" + UUID.randomUUID();
                providerEventService.processProviderEvent(new ProviderCallbackEvent(
                        ringEventId, call.getProviderCallId(), ProviderEventType.RINGING, Instant.now(), null
                ));

                // Provider failure check
                if (random.nextDouble() < request.providerFailureRate()) {
                    providerFailures.incrementAndGet();
                    failedCount.incrementAndGet();
                    String failEventId = "EVT-FAIL-" + UUID.randomUUID();
                    providerEventService.processProviderEvent(new ProviderCallbackEvent(
                        failEventId, call.getProviderCallId(), ProviderEventType.FAILED, Instant.now(), "TELCO_LINE_BUSY"
                    ));
                    continue;
                }

                // Check Borrower Pickup
                boolean borrowerAnswered = random.nextDouble() < currentAnswerRate;
                if (borrowerAnswered) {
                    answeredCount.incrementAndGet();

                    // Event 2: ANSWERED
                    String answerEventId = "EVT-ANS-" + UUID.randomUUID();
                    providerEventService.processProviderEvent(new ProviderCallbackEvent(
                            answerEventId, call.getProviderCallId(), ProviderEventType.ANSWERED, Instant.now(), null
                    ));

                    // Verify state after ANSWERED
                    CallRecord updated = callRecordRepository.findById(call.getId()).orElseThrow();

                    // CRITICAL INVARIANT VERIFICATION:
                    // A call must NEVER be marked CONNECTED unless an eligible agent is assigned!
                    if (updated.getStatus() == CallStatus.CONNECTED) {
                        if (updated.getAgentId() == null) {
                            String violation = "CRITICAL INVARIANT VIOLATION: Call id=" + updated.getId() + " is CONNECTED with NULL agentId!";
                            log.error(violation);
                            invariantViolations.add(violation);
                        } else {
                            connectedCount.incrementAndGet();
                            totalTalkTimeAgentSeconds += request.averageTalkTimeSeconds();
                        }

                        // Complete call
                        String completeEventId = "EVT-COMP-" + UUID.randomUUID();
                        providerEventService.processProviderEvent(new ProviderCallbackEvent(
                                completeEventId, call.getProviderCallId(), ProviderEventType.COMPLETED, Instant.now(), null
                        ));
                        completedCount.incrementAndGet();

                        // Transition agent back from WRAP_UP to AVAILABLE
                        if (updated.getAgentId() != null) {
                            agentService.releaseAgent(updated.getAgentId());
                        }
                    } else if (updated.getStatus() == CallStatus.CANCELLED) {
                        // Call was safely abandoned because no agent was free
                        log.info("Call id={} safely abandoned per safety invariant.", updated.getId());
                    }
                } else {
                    // Unanswered / No Answer
                    failedCount.incrementAndGet();
                    String noAnsEventId = "EVT-NOANS-" + UUID.randomUUID();
                    providerEventService.processProviderEvent(new ProviderCallbackEvent(
                            noAnsEventId, call.getProviderCallId(), ProviderEventType.FAILED, Instant.now(), "NO_ANSWER"
                    ));
                }
            }
        }

        long totalDialingTimeMs = System.currentTimeMillis() - startTime;
        int abandoned = providerEventService.getAbandonedCallCount();

        // Calculate Agent Utilization
        // Total possible agent time = numberOfAgents * (simulation duration in sec)
        double totalAgentCapacitySec = (double) request.numberOfAgents() * request.simulationDurationSeconds();
        double agentUtilizationPct = totalAgentCapacitySec > 0
                ? Math.min(98.5, (totalTalkTimeAgentSeconds / totalAgentCapacitySec) * 100.0)
                : 0.0;

        double avgAgentIdleTimeSec = Math.max(0.0, (totalAgentCapacitySec - totalTalkTimeAgentSeconds) / request.numberOfAgents());

        boolean zeroOrphanInvariantPreserved = invariantViolations.isEmpty();

        SimulationResultDto result = new SimulationResultDto(
                simId,
                request.scenarioName() != null ? request.scenarioName() : "Custom Scenario",
                request.dialingMode(),
                request.numberOfAgents(),
                request.numberOfBorrowers(),
                initiatedCount.get(),
                answeredCount.get(),
                connectedCount.get(),
                completedCount.get(),
                failedCount.get(),
                abandoned,
                Math.round(agentUtilizationPct * 100.0) / 100.0,
                Math.round(avgAgentIdleTimeSec * 100.0) / 100.0,
                providerFailures.get(),
                safetyApprovals.get(),
                safetyReductions.get(),
                safetyRejections.get(),
                fallbacks.get(),
                totalDialingTimeMs,
                zeroOrphanInvariantPreserved,
                invariantViolations,
                Instant.now()
        );

        resultsCache.put(simId, result);
        log.info("Simulation {} finished. Initiated: {}, Connected: {}, Abandoned: {}, Utilization: {}%, Invariants Preserved: {}",
                simId, result.totalCallsInitiated(), result.totalConnectedCalls(), result.abandonedCalls(), result.agentUtilizationPercentage(), result.zeroOrphanInvariantPreserved());

        return result;
    }

    public SimulationResultDto getSimulationResult(String simulationId) {
        return resultsCache.get(simulationId);
    }

    @Transactional
    public Campaign setupSimulationCampaign(SimulationRunRequest request, String simId) {
        Campaign campaign = new Campaign(
                "Simulation-" + simId.substring(0, 8),
                request.dialingMode(),
                request.numberOfAgents() * 2
        );
        campaign.setStatus(CampaignStatus.ACTIVE);
        Campaign savedCampaign = campaignRepository.save(campaign);

        // Seed Agents
        List<Agent> agents = new ArrayList<>();
        for (int i = 1; i <= request.numberOfAgents(); i++) {
            Agent agent = new Agent("SimAgent-" + i, AgentStatus.AVAILABLE, savedCampaign.getId());
            agents.add(agent);
        }
        agentRepository.saveAll(agents);

        // Seed Borrowers
        List<Borrower> borrowers = new ArrayList<>();
        for (int i = 1; i <= request.numberOfBorrowers(); i++) {
            Borrower borrower = new Borrower("SimBorrower-" + i, "+1555" + String.format("%06d", i), savedCampaign.getId());
            borrowers.add(borrower);
        }
        borrowerRepository.saveAll(borrowers);

        return savedCampaign;
    }
}
