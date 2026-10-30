package com.smartdialer.entity;

import com.smartdialer.enums.CallStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "calls", indexes = {
    @Index(name = "idx_call_campaign_status", columnList = "campaignId, status"),
    @Index(name = "idx_call_status", columnList = "status"),
    @Index(name = "idx_call_provider_call_id", columnList = "providerCallId"),
    @Index(name = "idx_call_idempotency_key", columnList = "idempotencyKey", unique = true)
})
public class CallRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private Long borrowerId;

    @Column
    private Long agentId;

    @Column(length = 120)
    private String providerCallId;

    @Column(length = 50)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CallStatus status;

    @Column
    private Instant initiatedAt;

    @Column
    private Instant answeredAt;

    @Column
    private Instant completedAt;

    @Column(length = 255)
    private String failureReason;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Version
    private Long version;

    public CallRecord() {
    }

    public CallRecord(Long campaignId, Long borrowerId, Long agentId, String idempotencyKey) {
        this.campaignId = campaignId;
        this.borrowerId = borrowerId;
        this.agentId = agentId;
        this.idempotencyKey = idempotencyKey;
        this.status = CallStatus.RESERVED;
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

    public Long getBorrowerId() {
        return borrowerId;
    }

    public void setBorrowerId(Long borrowerId) {
        this.borrowerId = borrowerId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getProviderCallId() {
        return providerCallId;
    }

    public void setProviderCallId(String providerCallId) {
        this.providerCallId = providerCallId;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public CallStatus getStatus() {
        return status;
    }

    public void setStatus(CallStatus status) {
        this.status = status;
    }

    public Instant getInitiatedAt() {
        return initiatedAt;
    }

    public void setInitiatedAt(Instant initiatedAt) {
        this.initiatedAt = initiatedAt;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(Instant answeredAt) {
        this.answeredAt = answeredAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
