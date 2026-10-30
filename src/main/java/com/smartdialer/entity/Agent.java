package com.smartdialer.entity;

import com.smartdialer.enums.AgentStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agents", indexes = {
    @Index(name = "idx_agent_status", columnList = "status"),
    @Index(name = "idx_agent_campaign_status", columnList = "campaignId, status"),
    @Index(name = "idx_agent_last_heartbeat", columnList = "lastHeartbeatAt")
})
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgentStatus status;

    @Column(nullable = false)
    private Instant lastHeartbeatAt;

    @Column
    private Long currentCallId;

    @Column
    private Long campaignId;

    @Version
    private Long version;

    public Agent() {
    }

    public Agent(String name, AgentStatus status, Long campaignId) {
        this.name = name;
        this.status = status != null ? status : AgentStatus.OFFLINE;
        this.campaignId = campaignId;
        this.lastHeartbeatAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Long getCurrentCallId() {
        return currentCallId;
    }

    public void setCurrentCallId(Long currentCallId) {
        this.currentCallId = currentCallId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
