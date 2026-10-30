package com.smartdialer.service.borrower;

import com.smartdialer.dto.BorrowerDto;
import com.smartdialer.dto.CreateBorrowerRequest;
import com.smartdialer.entity.Borrower;
import com.smartdialer.enums.BorrowerStatus;
import com.smartdialer.exception.ResourceNotFoundException;
import com.smartdialer.repository.BorrowerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class BorrowerService {

    private static final Logger log = LoggerFactory.getLogger(BorrowerService.class);

    private final BorrowerRepository borrowerRepository;

    public BorrowerService(BorrowerRepository borrowerRepository) {
        this.borrowerRepository = borrowerRepository;
    }

    @Transactional
    public BorrowerDto createBorrower(CreateBorrowerRequest request) {
        Borrower borrower = new Borrower(request.name(), request.phoneNumber(), request.campaignId());
        Borrower saved = borrowerRepository.save(borrower);
        log.info("Created borrower id={} name='{}' phone='{}'", saved.getId(), saved.getName(), saved.getPhoneNumber());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public BorrowerDto getBorrower(Long id) {
        Borrower borrower = findEntityById(id);
        return toDto(borrower);
    }

    @Transactional(readOnly = true)
    public List<BorrowerDto> listBorrowers(Long campaignId, BorrowerStatus status) {
        List<Borrower> list;
        if (campaignId != null && status != null) {
            list = borrowerRepository.findByCampaignIdAndStatus(campaignId, status);
        } else if (campaignId != null) {
            list = borrowerRepository.findAll().stream()
                    .filter(b -> campaignId.equals(b.getCampaignId()))
                    .toList();
        } else if (status != null) {
            list = borrowerRepository.findAll().stream()
                    .filter(b -> status.equals(b.getStatus()))
                    .toList();
        } else {
            list = borrowerRepository.findAll();
        }
        return list.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<Borrower> findEligibleBorrowers(Long campaignId, int maxAttempts, int limit) {
        return borrowerRepository.findEligibleBorrowers(
                campaignId,
                maxAttempts,
                Instant.now(),
                PageRequest.of(0, limit)
        );
    }

    /**
     * Atomically reserves an eligible borrower.
     * Prevents simultaneous reservation by multiple workers.
     */
    @Transactional
    public boolean reserveBorrower(Long borrowerId) {
        int updated = borrowerRepository.reserveBorrowerAtomic(borrowerId, Instant.now());
        if (updated == 1) {
            log.info("Successfully reserved borrower id={}", borrowerId);
            return true;
        } else {
            log.warn("Failed to reserve borrower id={} - borrower not eligible or already reserved", borrowerId);
            return false;
        }
    }

    @Transactional
    public void markDialing(Long borrowerId) {
        Borrower borrower = findEntityById(borrowerId);
        borrower.setStatus(BorrowerStatus.DIALING);
        borrowerRepository.save(borrower);
    }

    @Transactional
    public void markConnected(Long borrowerId) {
        Borrower borrower = findEntityById(borrowerId);
        borrower.setStatus(BorrowerStatus.CONNECTED);
        borrowerRepository.save(borrower);
    }

    @Transactional
    public void markCompleted(Long borrowerId) {
        Borrower borrower = findEntityById(borrowerId);
        borrower.setStatus(BorrowerStatus.COMPLETED);
        borrower.setLastAttemptAt(Instant.now());
        borrowerRepository.save(borrower);
        log.info("Borrower id={} call completed successfully", borrowerId);
    }

    @Transactional
    public void markFailed(Long borrowerId, int cooldownSeconds, int maxAttempts) {
        Borrower borrower = findEntityById(borrowerId);
        int newAttempts = borrower.getAttemptCount() + 1;
        borrower.setAttemptCount(newAttempts);
        borrower.setLastAttemptAt(Instant.now());

        if (newAttempts >= maxAttempts) {
            borrower.setStatus(BorrowerStatus.FAILED);
            borrower.setCooldownUntil(null);
            log.warn("Borrower id={} exceeded max attempts ({}/{}). Marked as FAILED.", borrowerId, newAttempts, maxAttempts);
        } else {
            borrower.setStatus(BorrowerStatus.COOLDOWN);
            borrower.setCooldownUntil(Instant.now().plusSeconds(cooldownSeconds));
            log.info("Borrower id={} attempt {}/{} failed. Cooldown until {}", borrowerId, newAttempts, maxAttempts, borrower.getCooldownUntil());
        }
        borrowerRepository.save(borrower);
    }

    @Transactional
    public void releaseReservation(Long borrowerId) {
        Borrower borrower = findEntityById(borrowerId);
        if (borrower.getStatus() == BorrowerStatus.RESERVED || borrower.getStatus() == BorrowerStatus.DIALING) {
            borrower.setStatus(BorrowerStatus.PENDING);
            borrowerRepository.save(borrower);
            log.info("Released reservation for borrower id={}", borrowerId);
        }
    }

    @Transactional(readOnly = true)
    public long countEligibleBorrowers(Long campaignId, int maxAttempts) {
        return findEligibleBorrowers(campaignId, maxAttempts, Integer.MAX_VALUE).size();
    }

    public Borrower findEntityById(Long id) {
        return borrowerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrower not found with id: " + id));
    }

    public BorrowerDto toDto(Borrower borrower) {
        return new BorrowerDto(
                borrower.getId(),
                borrower.getName(),
                borrower.getPhoneNumber(),
                borrower.getCampaignId(),
                borrower.getStatus(),
                borrower.getAttemptCount(),
                borrower.getLastAttemptAt(),
                borrower.getCooldownUntil()
        );
    }
}
