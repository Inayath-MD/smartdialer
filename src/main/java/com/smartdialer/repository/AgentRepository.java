package com.smartdialer.repository;

import com.smartdialer.entity.Agent;
import com.smartdialer.enums.AgentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {

    List<Agent> findByStatus(AgentStatus status);

    List<Agent> findByCampaignIdAndStatus(Long campaignId, AgentStatus status);

    long countByStatus(AgentStatus status);

    long countByCampaignIdAndStatus(Long campaignId, AgentStatus status);

    long countByCampaignIdAndStatusIn(Long campaignId, Collection<AgentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Agent a WHERE a.id = :id")
    Optional<Agent> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT a FROM Agent a WHERE a.status = :status AND (a.campaignId IS NULL OR a.campaignId = :campaignId) ORDER BY a.id ASC")
    List<Agent> findAvailableAgentsForCampaign(@Param("campaignId") Long campaignId, @Param("status") AgentStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE Agent a SET a.status = :newStatus, a.currentCallId = :callId WHERE a.id = :id AND a.status = :expectedStatus")
    int transitionStatusAtomic(
            @Param("id") Long id,
            @Param("expectedStatus") AgentStatus expectedStatus,
            @Param("newStatus") AgentStatus newStatus,
            @Param("callId") Long callId
    );

    @Modifying
    @Query("UPDATE Agent a SET a.status = com.smartdialer.enums.AgentStatus.RESERVED, a.currentCallId = :callId WHERE a.id = :id AND a.status = com.smartdialer.enums.AgentStatus.AVAILABLE")
    int reserveAgentAtomic(@Param("id") Long id, @Param("callId") Long callId);

    @Modifying
    @Query("UPDATE Agent a SET a.status = com.smartdialer.enums.AgentStatus.AVAILABLE, a.currentCallId = NULL WHERE a.id = :id")
    int releaseAgent(@Param("id") Long id);

    @Query("SELECT a FROM Agent a WHERE a.lastHeartbeatAt < :threshold AND a.status <> com.smartdialer.enums.AgentStatus.OFFLINE")
    List<Agent> findStaleHeartbeatAgents(@Param("threshold") Instant threshold);
}
