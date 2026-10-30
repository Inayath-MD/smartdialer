package com.smartdialer.entity;

import com.smartdialer.enums.SafetyDecision;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "safety_decisions", indexes = {
    @Index(name = "idx_safety_decision_campaign", columnList = "campaignId, createdAt")
})
public class SafetyDecisionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private int requestedCalls;

    @Column(nullable = false)
    private int approvedCalls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SafetyDecision decision;

    @Column(nullable = false, length = 1000)
    private String reasoning;

    @Column(nullable = false)
    private Instant createdAt;

    public SafetyDecisionRecord() {
    }

    public SafetyDecisionRecord(Long campaignId, int requestedCalls, int approvedCalls, SafetyDecision decision, String reasoning) {
        this.campaignId = campaignId;
        this.requestedCalls = requestedCalls;
        this.approvedCalls = approvedCalls;
        this.decision = decision;
        this.reasoning = reasoning;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public int getRequestedCalls() {
        return requestedCalls;
    }

    public void setRequestedCalls(int requestedCalls) {
        this.requestedCalls = requestedCalls;
    }

    public int getApprovedCalls() {
        return approvedCalls;
    }

    public void setApprovedCalls(int approvedCalls) {
        this.approvedCalls = approvedCalls;
    }

    public SafetyDecision getDecision() {
        return decision;
    }

    public void setDecision(SafetyDecision decision) {
        this.decision = decision;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
