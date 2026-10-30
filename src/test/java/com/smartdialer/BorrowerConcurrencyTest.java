package com.smartdialer;

import com.smartdialer.entity.Borrower;
import com.smartdialer.enums.BorrowerStatus;
import com.smartdialer.repository.BorrowerRepository;
import com.smartdialer.service.borrower.BorrowerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class BorrowerConcurrencyTest {

    @Autowired
    private BorrowerService borrowerService;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Test
    @DisplayName("MANDATORY TEST 2: Two workers cannot reserve the same borrower simultaneously")
    public void testTwoWorkersCannotReserveSameBorrower() throws InterruptedException {
        // Arrange
        Borrower borrower = new Borrower("Borrower-Concurrency-Test", "+15551234567", 1L);
        Borrower saved = borrowerRepository.save(borrower);
        Long borrowerId = saved.getId();

        int numberOfWorkers = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfWorkers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numberOfWorkers);
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failureCounter = new AtomicInteger(0);

        for (int i = 0; i < numberOfWorkers; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    boolean reserved = borrowerService.reserveBorrower(borrowerId);
                    if (reserved) {
                        successCounter.incrementAndGet();
                    } else {
                        failureCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCounter.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one worker must succeed in reserving the borrower
        assertEquals(1, successCounter.get(), "Exactly one worker must succeed in reserving the borrower");
        assertEquals(numberOfWorkers - 1, failureCounter.get(), "All other workers must fail");

        Borrower postReservation = borrowerRepository.findById(borrowerId).orElseThrow();
        assertEquals(BorrowerStatus.RESERVED, postReservation.getStatus(), "Borrower status must be RESERVED");
    }
}
