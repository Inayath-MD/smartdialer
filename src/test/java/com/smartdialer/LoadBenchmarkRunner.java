package com.smartdialer;

import com.smartdialer.dto.DialerRunResponse;
import com.smartdialer.entity.Agent;
import com.smartdialer.entity.Borrower;
import com.smartdialer.entity.Campaign;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.enums.CampaignStatus;
import com.smartdialer.enums.DialingMode;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.repository.BorrowerRepository;
import com.smartdialer.repository.CampaignRepository;
import com.smartdialer.service.dialer.ProgressiveDialerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class LoadBenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadBenchmarkRunner.class);

    @Autowired
    private ProgressiveDialerService progressiveDialerService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private com.smartdialer.service.agent.AgentService agentService;

    @Test
    @DisplayName("BENCHMARK: Measure allocation throughput and latency for 100 and 1,000 agents")
    public void runBenchmark() {
        runBenchmarkForScale(100, 300);
        runBenchmarkForScale(1000, 3000);
    }

    private void runBenchmarkForScale(int agentCount, int borrowerCount) {
        Campaign campaign = new Campaign("Benchmark-" + agentCount, DialingMode.PROGRESSIVE, agentCount * 2);
        campaign.setStatus(CampaignStatus.ACTIVE);
        Campaign savedCampaign = campaignRepository.save(campaign);
        Long campaignId = savedCampaign.getId();

        List<Agent> agents = new ArrayList<>(agentCount);
        for (int i = 1; i <= agentCount; i++) {
            agents.add(new Agent("BenchAgent-" + i, AgentStatus.AVAILABLE, campaignId));
        }
        agentRepository.saveAll(agents);

        List<Borrower> borrowers = new ArrayList<>(borrowerCount);
        for (int i = 1; i <= borrowerCount; i++) {
            borrowers.add(new Borrower("BenchBorrower-" + i, "+1555" + String.format("%07d", i), campaignId));
        }
        borrowerRepository.saveAll(borrowers);

        List<Long> latencies = new ArrayList<>();
        int iterations = 10;
        int totalAllocated = 0;

        for (int iter = 0; iter < iterations; iter++) {
            // Re-release 20 agents each round using atomic service release
            for (int i = 0; i < Math.min(20, agents.size()); i++) {
                agentService.releaseAgent(agents.get(i).getId());
            }

            long start = System.nanoTime();
            DialerRunResponse response = progressiveDialerService.runProgressiveDialing(campaignId);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            latencies.add(elapsedMs);
            totalAllocated += response.allocatedCalls();
        }

        Collections.sort(latencies);
        long p50 = latencies.get(latencies.size() / 2);
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double throughput = (totalAllocated / (latencies.stream().mapToLong(Long::longValue).sum() / 1000.0));

        log.info("==================================================");
        log.info("BENCHMARK RESULTS FOR {} AGENTS ({} iterations):", agentCount, iterations);
        log.info("Total Allocated Calls: {}", totalAllocated);
        log.info("Allocation Throughput: {} calls/sec", String.format("%.2f", throughput));
        log.info("Average Latency: {} ms", String.format("%.2f", avgLatency));
        log.info("P50 Latency: {} ms", p50);
        log.info("P95 Latency: {} ms", p95);
        log.info("==================================================");

        assertNotNull(savedCampaign);
    }
}
