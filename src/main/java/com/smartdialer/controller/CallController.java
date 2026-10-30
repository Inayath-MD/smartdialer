package com.smartdialer.controller;

import com.smartdialer.dto.CallRecordDto;
import com.smartdialer.entity.CallRecord;
import com.smartdialer.exception.ResourceNotFoundException;
import com.smartdialer.repository.CallRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calls")
@Tag(name = "Calls", description = "Call records and tracking")
public class CallController {

    private final CallRecordRepository callRecordRepository;

    public CallController(CallRecordRepository callRecordRepository) {
        this.callRecordRepository = callRecordRepository;
    }

    @GetMapping
    @Operation(summary = "List calls with optional campaign filter")
    public ResponseEntity<List<CallRecordDto>> listCalls(
            @RequestParam(required = false) Long campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        List<CallRecord> list;
        if (campaignId != null) {
            list = callRecordRepository.findByCampaignId(campaignId, PageRequest.of(page, size));
        } else {
            list = callRecordRepository.findAll(PageRequest.of(page, size)).getContent();
        }
        return ResponseEntity.ok(list.stream().map(this::toDto).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get call record by ID")
    public ResponseEntity<CallRecordDto> getCall(@PathVariable Long id) {
        CallRecord call = callRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Call not found with id: " + id));
        return ResponseEntity.ok(toDto(call));
    }

    private CallRecordDto toDto(CallRecord c) {
        return new CallRecordDto(
                c.getId(),
                c.getCampaignId(),
                c.getBorrowerId(),
                c.getAgentId(),
                c.getProviderCallId(),
                c.getProviderName(),
                c.getStatus(),
                c.getInitiatedAt(),
                c.getAnsweredAt(),
                c.getCompletedAt(),
                c.getFailureReason(),
                c.getIdempotencyKey()
        );
    }
}
