package com.smartdialer.entity;

import com.smartdialer.enums.BorrowerStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "borrowers", indexes = {
    @Index(name = "idx_borrower_campaign_status", columnList = "campaignId, status"),
    @Index(name = "idx_borrower_status", columnList = "status"),
    @Index(name = "idx_borrower_phone", columnList = "phoneNumber")
})
public class Borrower {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 30)
    private String phoneNumber;

    @Column(nullable = false)
    private Long campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BorrowerStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column
    private Instant lastAttemptAt;

    @Column
    private Instant cooldownUntil;

    @Version
    private Long version;

    public Borrower() {
    }

    public Borrower(String name, String phoneNumber, Long campaignId) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.campaignId = campaignId;
        this.status = BorrowerStatus.PENDING;
        this.attemptCount = 0;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public BorrowerStatus getStatus() {
        return status;
    }

    public void setStatus(BorrowerStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public Instant getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(Instant cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
