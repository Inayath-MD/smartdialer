package com.smartdialer.repository;

import com.smartdialer.entity.SafetyDecisionRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SafetyDecisionRecordRepository extends JpaRepository<SafetyDecisionRecord, Long> {
    List<SafetyDecisionRecord> findByCampaignIdOrderByCreatedAtDesc(Long campaignId, Pageable pageable);
    List<SafetyDecisionRecord> findTop100ByOrderByCreatedAtDesc();
}
