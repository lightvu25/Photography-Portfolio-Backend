package com.photography.backend.booking.dto;

import com.photography.backend.booking.entity.Slot;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BlockedSlotRequest {

    @NotNull(message = "Blocked date is required")
    private LocalDate blockedDate;

    @NotNull(message = "Slot is required")
    private Slot slot;

    private String reason;
}
