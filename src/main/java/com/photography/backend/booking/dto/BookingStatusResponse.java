package com.photography.backend.booking.dto;

import com.photography.backend.booking.entity.BookingStatus;
import com.photography.backend.booking.entity.Slot;
import com.photography.backend.payment.entity.PaymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BookingStatusResponse(
        String bookingCode,
        BookingStatus status,
        String customerName,
        String packageName,
        List<SlotInfo> slots,
        LocalDateTime expiresAt,
        PaymentInfo payment
) {
    public record SlotInfo(LocalDate bookingDate, Slot slot) {
    }

    public record PaymentInfo(PaymentStatus status, Long amount, String qrReference, String qrImageUrl) {
    }
}
