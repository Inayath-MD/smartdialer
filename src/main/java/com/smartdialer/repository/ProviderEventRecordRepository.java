package com.smartdialer.repository;

import com.smartdialer.entity.ProviderEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderEventRecordRepository extends JpaRepository<ProviderEventRecord, Long> {
    boolean existsByProviderEventId(String providerEventId);
    Optional<ProviderEventRecord> findByProviderEventId(String providerEventId);
}
