package com.smartdialer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBorrowerRequest(
        @NotBlank(message = "Borrower name is required")
        String name,
        @NotBlank(message = "Phone number is required")
        String phoneNumber,
        @NotNull(message = "Campaign ID is required")
        Long campaignId
) {}
