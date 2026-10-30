package com.smartdialer.entity;

import com.smartdialer.enums.CampaignStatus;
import com.smartdialer.enums.DialingMode;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "campaigns", indexes = {
    @Index(name = "idx_campaign_status", columnList = "status")
})
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CampaignStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DialingMode dialingMode;

    @Column(nullable = false)
    private int maxConcurrentCalls;

    @Column(nullable = false)
    private double targetAbandonmentRate;

    @Column(nullable = false)
    private int maxAttemptsPerBorrower;

    @Column(nullable = false)
    private int cooldownPeriodSeconds;

    @Column(nullable = false)
    private Instant createdAt;

    public Campaign() {
    }

    public Campaign(String name, DialingMode dialingMode, int maxConcurrentCalls) {
        this.name = name;
        this.dialingMode = dialingMode != null ? dialingMode : DialingMode.PROGRESSIVE;
        this.status = CampaignStatus.CREATED;
        this.maxConcurrentCalls = maxConcurrentCalls > 0 ? maxConcurrentCalls : 10;
        this.targetAbandonmentRate = 0.03; // 3% industry standard
        this.maxAttemptsPerBorrower = 3;
        this.cooldownPeriodSeconds = 300; // 5 minutes cooldown
        this.createdAt = Instant.now();
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

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public DialingMode getDialingMode() {
        return dialingMode;
    }

    public void setDialingMode(DialingMode dialingMode) {
        this.dialingMode = dialingMode;
    }

    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    public void setMaxConcurrentCalls(int maxConcurrentCalls) {
        this.maxConcurrentCalls = maxConcurrentCalls;
    }

    public double getTargetAbandonmentRate() {
        return targetAbandonmentRate;
    }

    public void setTargetAbandonmentRate(double targetAbandonmentRate) {
        this.targetAbandonmentRate = targetAbandonmentRate;
    }

    public int getMaxAttemptsPerBorrower() {
        return maxAttemptsPerBorrower;
    }

    public void setMaxAttemptsPerBorrower(int maxAttemptsPerBorrower) {
        this.maxAttemptsPerBorrower = maxAttemptsPerBorrower;
    }

    public int getCooldownPeriodSeconds() {
        return cooldownPeriodSeconds;
    }

    public void setCooldownPeriodSeconds(int cooldownPeriodSeconds) {
        this.cooldownPeriodSeconds = cooldownPeriodSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
