package com.photography.backend.payment.service;

import com.photography.backend.booking.entity.Booking;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.payment.entity.Payment;
import com.photography.backend.payment.entity.PaymentStatus;
import com.photography.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Value("${app.bank.code}")
    private String bankCode;

    @Value("${app.bank.account-number}")
    private String accountNumber;

    @Value("${app.bank.account-name}")
    private String accountName;

    @Value("${app.bank.deposit-amount}")
    private Long depositAmount;

    @Transactional
    public Payment createPaymentForBooking(Booking booking) {
        log.info("Creating payment for booking: {}", booking.getBookingCode());
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(depositAmount);
        payment.setStatus(PaymentStatus.UNPAID);
        payment.setQrReference(booking.getBookingCode());
        return paymentRepository.save(payment);
    }

    public String generateVietQrUrl(Payment payment) {
        String encodedName = URLEncoder.encode(accountName, StandardCharsets.UTF_8);
        String encodedInfo = URLEncoder.encode(payment.getQrReference(), StandardCharsets.UTF_8);
        return String.format("https://img.vietqr.io/image/%s-%s-compact2.png?amount=%d&addInfo=%s&accountName=%s",
                bankCode, accountNumber, payment.getAmount(), encodedInfo, encodedName);
    }

    @Transactional
    public Payment markAsPaid(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Payment not found", "PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Payment is already marked as paid", "ALREADY_PAID", HttpStatus.BAD_REQUEST);
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        log.info("Payment {} marked as PAID", paymentId);
        return paymentRepository.save(payment);
    }
}