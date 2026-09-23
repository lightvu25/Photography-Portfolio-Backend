package com.photography.backend.booking.controller;

import com.photography.backend.booking.dto.BlockedSlotRequest;
import com.photography.backend.booking.entity.BlockedSlot;
import com.photography.backend.booking.repository.BlockedSlotRepository;
import com.photography.backend.booking.repository.BookingSlotRepository;
import com.photography.backend.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/blocked-slots")
@RequiredArgsConstructor
public class AdminBlockedSlotController {

    private final BlockedSlotRepository blockedSlotRepository;
    private final BookingSlotRepository bookingSlotRepository;

    @GetMapping
    public ResponseEntity<List<BlockedSlot>> getBlockedSlots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        if (start != null && end != null) {
            return ResponseEntity.ok(blockedSlotRepository.findBetween(start, end));
        }
        return ResponseEntity.ok(blockedSlotRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<BlockedSlot> blockSlot(@Valid @RequestBody BlockedSlotRequest request) {
        boolean occupied = bookingSlotRepository
                .findActiveSlot(request.getBlockedDate(), request.getSlot(), LocalDateTime.now())
                .isPresent();
        if (occupied) {
            throw new BusinessException("Cannot block a slot with an active booking",
                    "SLOT_OCCUPIED", HttpStatus.CONFLICT);
        }

        BlockedSlot blockedSlot = new BlockedSlot();
        blockedSlot.setBlockedDate(request.getBlockedDate());
        blockedSlot.setSlot(request.getSlot());
        blockedSlot.setReason(request.getReason());

        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(blockedSlotRepository.save(blockedSlot));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Slot is already blocked", "ALREADY_BLOCKED", HttpStatus.CONFLICT);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unblockSlot(@PathVariable Long id) {
        BlockedSlot blockedSlot = blockedSlotRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Blocked slot not found", "NOT_FOUND", HttpStatus.NOT_FOUND));
        blockedSlotRepository.delete(blockedSlot);
        return ResponseEntity.noContent().build();
    }
}
