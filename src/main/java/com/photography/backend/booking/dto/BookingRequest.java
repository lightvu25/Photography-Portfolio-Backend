package com.photography.backend.booking.dto;

import com.photography.backend.booking.entity.Slot;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BookingRequest {

    @NotNull(message = "Package ID is required")
    private Long packageId;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotBlank(message = "Phone is required")
    private String phone;

    private String instagram;

    @NotNull(message = "Booking date is required")
    @FutureOrPresent(message = "Booking date must not be in the past")
    private LocalDate bookingDate;

    // Slot is optional for FULL_DAY, but required for HALF_DAY. We will validate in service.
    private Slot slot;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "Turnstile token is required")
    private String turnstileToken;
}
