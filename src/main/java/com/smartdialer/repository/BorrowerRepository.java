package com.smartdialer.repository;

import com.smartdialer.entity.Borrower;
import com.smartdialer.enums.BorrowerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    List<Borrower> findByCampaignIdAndStatus(Long campaignId, BorrowerStatus status);

    long countByCampaignIdAndStatus(Long campaignId, BorrowerStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Borrower b WHERE b.id = :id")
    Optional<Borrower> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT b FROM Borrower b WHERE b.campaignId = :campaignId " +
           "AND (b.status = com.smartdialer.enums.BorrowerStatus.PENDING " +
           "     OR (b.status = com.smartdialer.enums.BorrowerStatus.COOLDOWN AND b.cooldownUntil <= :now)) " +
           "AND b.attemptCount < :maxAttempts ORDER BY b.id ASC")
    List<Borrower> findEligibleBorrowers(
            @Param("campaignId") Long campaignId,
            @Param("maxAttempts") int maxAttempts,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE Borrower b SET b.status = com.smartdialer.enums.BorrowerStatus.RESERVED " +
           "WHERE b.id = :id AND (b.status = com.smartdialer.enums.BorrowerStatus.PENDING " +
           "     OR (b.status = com.smartdialer.enums.BorrowerStatus.COOLDOWN AND b.cooldownUntil <= :now))")
    int reserveBorrowerAtomic(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE Borrower b SET b.status = :status, b.cooldownUntil = :cooldownUntil, b.attemptCount = b.attemptCount + 1, b.lastAttemptAt = :now WHERE b.id = :id")
    int updateAttemptOutcome(
            @Param("id") Long id,
            @Param("status") BorrowerStatus status,
            @Param("cooldownUntil") Instant cooldownUntil,
            @Param("now") Instant now
    );
}
