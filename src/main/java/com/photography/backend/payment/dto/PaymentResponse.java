package com.photography.backend.payment.dto;

import com.photography.backend.payment.entity.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long bookingId,
        String bookingCode,
        Long amount,
        PaymentStatus status,
        String qrReference,
        String qrImageUrl,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
}
