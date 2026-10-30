package com.smartdialer.service.agent;

import com.smartdialer.dto.AgentDto;
import com.smartdialer.dto.CreateAgentRequest;
import com.smartdialer.entity.Agent;
import com.smartdialer.enums.AgentStatus;
import com.smartdialer.exception.ConcurrentReservationException;
import com.smartdialer.exception.ResourceNotFoundException;
import com.smartdialer.repository.AgentRepository;
import com.smartdialer.state.AgentStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRepository agentRepository;
    private final AgentStateMachine agentStateMachine;

    public AgentService(AgentRepository agentRepository, AgentStateMachine agentStateMachine) {
        this.agentRepository = agentRepository;
        this.agentStateMachine = agentStateMachine;
    }

    @Transactional
    public AgentDto createAgent(CreateAgentRequest request) {
        Agent agent = new Agent(request.name(), AgentStatus.OFFLINE, request.campaignId());
        Agent saved = agentRepository.save(agent);
        log.info("Created agent id={} name='{}'", saved.getId(), saved.getName());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public AgentDto getAgent(Long id) {
        Agent agent = findEntityById(id);
        return toDto(agent);
    }

    @Transactional(readOnly = true)
    public List<AgentDto> listAgents(Long campaignId, AgentStatus status) {
        List<Agent> list;
        if (campaignId != null && status != null) {
            list = agentRepository.findByCampaignIdAndStatus(campaignId, status);
        } else if (status != null) {
            list = agentRepository.findByStatus(status);
        } else if (campaignId != null) {
            list = agentRepository.findAll().stream()
                    .filter(a -> campaignId.equals(a.getCampaignId()))
                    .toList();
        } else {
            list = agentRepository.findAll();
        }
        return list.stream().map(this::toDto).toList();
    }

    @Transactional
    public AgentDto updateStatus(Long id, AgentStatus newStatus) {
        Agent agent = findEntityById(id);
        agentStateMachine.validateTransition(agent.getStatus(), newStatus);
        agent.setStatus(newStatus);
        if (newStatus == AgentStatus.OFFLINE || newStatus == AgentStatus.AVAILABLE) {
            agent.setCurrentCallId(null);
        }
        agent.setLastHeartbeatAt(Instant.now());
        Agent saved = agentRepository.save(agent);
        log.info("Agent id={} transitioned to status={}", id, newStatus);
        return toDto(saved);
    }

    @Transactional
    public void recordHeartbeat(Long id) {
        Agent agent = findEntityById(id);
        agent.setLastHeartbeatAt(Instant.now());
        agentRepository.save(agent);
    }

    /**
     * Atomically reserves an available agent for a specific call.
     * Uses atomic UPDATE to guarantee only ONE concurrent worker can successfully reserve.
     */
    @Transactional
    public boolean reserveAgent(Long agentId, Long callId) {
        int updated = agentRepository.reserveAgentAtomic(agentId, callId);
        if (updated == 1) {
            log.info("Successfully reserved agent id={} for callId={}", agentId, callId);
            return true;
        } else {
            log.warn("Failed to reserve agent id={} - agent was not in AVAILABLE state", agentId);
            return false;
        }
    }

    @Transactional
    public void releaseAgent(Long agentId) {
        agentRepository.releaseAgent(agentId);
        log.info("Released agent id={} back to AVAILABLE", agentId);
    }

    @Transactional
    public void markDialing(Long agentId, Long callId) {
        Agent agent = findEntityById(agentId);
        agentStateMachine.validateTransition(agent.getStatus(), AgentStatus.DIALING);
        agent.setStatus(AgentStatus.DIALING);
        agent.setCurrentCallId(callId);
        agentRepository.save(agent);
    }

    @Transactional
    public void markConnected(Long agentId, Long callId) {
        Agent agent = findEntityById(agentId);
        agentStateMachine.validateTransition(agent.getStatus(), AgentStatus.CONNECTED);
        agent.setStatus(AgentStatus.CONNECTED);
        agent.setCurrentCallId(callId);
        agentRepository.save(agent);
    }

    @Transactional
    public void markWrapUp(Long agentId) {
        Agent agent = findEntityById(agentId);
        agentStateMachine.validateTransition(agent.getStatus(), AgentStatus.WRAP_UP);
        agent.setStatus(AgentStatus.WRAP_UP);
        agentRepository.save(agent);
    }

    @Transactional(readOnly = true)
    public List<Agent> getAvailableAgentsForCampaign(Long campaignId, int limit) {
        return agentRepository.findAvailableAgentsForCampaign(
                campaignId,
                AgentStatus.AVAILABLE,
                PageRequest.of(0, limit)
        );
    }

    @Transactional(readOnly = true)
    public long countAvailableAgents(Long campaignId) {
        return agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.AVAILABLE);
    }

    @Transactional(readOnly = true)
    public long countReservedAgents(Long campaignId) {
        return agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.RESERVED);
    }

    @Transactional(readOnly = true)
    public long countConnectedAgents(Long campaignId) {
        return agentRepository.countByCampaignIdAndStatus(campaignId, AgentStatus.CONNECTED);
    }

    @Transactional
    public int checkAndCleanStaleHeartbeats(Duration timeout) {
        Instant threshold = Instant.now().minus(timeout);
        List<Agent> staleAgents = agentRepository.findStaleHeartbeatAgents(threshold);
        int count = 0;
        for (Agent agent : staleAgents) {
            if (agent.getStatus() != AgentStatus.OFFLINE) {
                log.warn("Agent id={} missed heartbeat threshold (last={}). Transitioning to OFFLINE.",
                        agent.getId(), agent.getLastHeartbeatAt());
                agent.setStatus(AgentStatus.OFFLINE);
                agent.setCurrentCallId(null);
                agentRepository.save(agent);
                count++;
            }
        }
        return count;
    }

    public Agent findEntityById(Long id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found with id: " + id));
    }

    public AgentDto toDto(Agent agent) {
        return new AgentDto(
                agent.getId(),
                agent.getName(),
                agent.getStatus(),
                agent.getLastHeartbeatAt(),
                agent.getCurrentCallId(),
                agent.getCampaignId()
        );
    }
}
