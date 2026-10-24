package com.photography.backend.payment.controller;

import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.payment.dto.PaymentResponse;
import com.photography.backend.payment.entity.Payment;
import com.photography.backend.payment.repository.PaymentRepository;
import com.photography.backend.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        List<PaymentResponse> payments = paymentRepository.findAll().stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(payments);
    }

    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<PaymentResponse> markAsPaid(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(paymentService.markAsPaid(id)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deletePayment(@PathVariable Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Payment not found", "PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND));
        paymentRepository.delete(payment);
        return ResponseEntity.noContent().build();
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBooking().getId(),
                payment.getBooking().getBookingCode(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getQrReference(),
                paymentService.generateVietQrUrl(payment),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }
}
