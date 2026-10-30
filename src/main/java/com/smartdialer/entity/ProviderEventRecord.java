package com.smartdialer.entity;

import com.smartdialer.enums.ProcessingStatus;
import com.smartdialer.enums.ProviderEventType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "provider_events", indexes = {
    @Index(name = "idx_provider_event_id", columnList = "providerEventId", unique = true),
    @Index(name = "idx_provider_event_call_id", columnList = "providerCallId")
})
public class ProviderEventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String providerEventId;

    @Column(nullable = false, length = 120)
    private String providerCallId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProviderEventType eventType;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProcessingStatus processingStatus;

    @Column(columnDefinition = "TEXT")
    private String payload;

    public ProviderEventRecord() {
    }

    public ProviderEventRecord(String providerEventId, String providerCallId, ProviderEventType eventType, String payload) {
        this.providerEventId = providerEventId;
        this.providerCallId = providerCallId;
        this.eventType = eventType;
        this.payload = payload;
        this.receivedAt = Instant.now();
        this.processingStatus = ProcessingStatus.RECEIVED;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public String getProviderCallId() {
        return providerCallId;
    }

    public void setProviderCallId(String providerCallId) {
        this.providerCallId = providerCallId;
    }

    public ProviderEventType getEventType() {
        return eventType;
    }

    public void setEventType(ProviderEventType eventType) {
        this.eventType = eventType;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
