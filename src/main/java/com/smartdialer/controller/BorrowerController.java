package com.smartdialer.controller;

import com.smartdialer.dto.BorrowerDto;
import com.smartdialer.dto.CreateBorrowerRequest;
import com.smartdialer.enums.BorrowerStatus;
import com.smartdialer.service.borrower.BorrowerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/borrowers")
@Tag(name = "Borrowers", description = "Borrower lead records and attempt history")
public class BorrowerController {

    private final BorrowerService borrowerService;

    public BorrowerController(BorrowerService borrowerService) {
        this.borrowerService = borrowerService;
    }

    @PostMapping
    @Operation(summary = "Register a new borrower lead for a campaign")
    public ResponseEntity<BorrowerDto> createBorrower(@Valid @RequestBody CreateBorrowerRequest request) {
        BorrowerDto created = borrowerService.createBorrower(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List borrower records with optional filtering")
    public ResponseEntity<List<BorrowerDto>> listBorrowers(
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) BorrowerStatus status
    ) {
        return ResponseEntity.ok(borrowerService.listBorrowers(campaignId, status));
    }
}
