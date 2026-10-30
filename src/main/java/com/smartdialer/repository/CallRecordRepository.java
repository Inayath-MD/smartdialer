package com.smartdialer.repository;

import com.smartdialer.entity.CallRecord;
import com.smartdialer.enums.CallStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {

    Optional<CallRecord> findByIdempotencyKey(String idempotencyKey);

    Optional<CallRecord> findByProviderCallId(String providerCallId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CallRecord c WHERE c.id = :id")
    Optional<CallRecord> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CallRecord c WHERE c.providerCallId = :providerCallId")
    Optional<CallRecord> findByProviderCallIdWithLock(@Param("providerCallId") String providerCallId);

    long countByCampaignIdAndStatus(Long campaignId, CallStatus status);

    long countByCampaignIdAndStatusIn(Long campaignId, Collection<CallStatus> statuses);

    List<CallRecord> findByCampaignId(Long campaignId, Pageable pageable);

    @Query("SELECT c FROM CallRecord c WHERE c.status = com.smartdialer.enums.CallStatus.RESERVED AND c.initiatedAt IS NULL")
    List<CallRecord> findUninitiatedReservedCalls();

    @Query("SELECT c FROM CallRecord c WHERE c.status IN (com.smartdialer.enums.CallStatus.INITIATED, com.smartdialer.enums.CallStatus.RINGING) AND c.initiatedAt < :threshold")
    List<CallRecord> findStaleInFlightCalls(@Param("threshold") Instant threshold);
}
