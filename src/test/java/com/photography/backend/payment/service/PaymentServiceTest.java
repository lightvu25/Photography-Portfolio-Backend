package com.photography.backend.payment.service;

import com.photography.backend.booking.controller.AdminBookingController;
import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.entity.Booking;
import com.photography.backend.booking.entity.Slot;
import com.photography.backend.booking.service.BookingService;
import com.photography.backend.common.exception.BusinessException;

import com.photography.backend.payment.entity.Payment;
import com.photography.backend.payment.entity.PaymentStatus;
import com.photography.backend.payment.repository.PaymentRepository;
import com.photography.backend.pricing.entity.PricingPackage;
import com.photography.backend.booking.entity.SessionType;
import com.photography.backend.pricing.repository.PricingPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)

@ActiveProfiles("test")
@Transactional
public class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AdminBookingController adminBookingController;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PricingPackageRepository pricingPackageRepository;

    private PricingPackage pack;
    private Booking booking;

    @BeforeEach
    void setUp() {
        pack = new PricingPackage();
        pack.setName("Half Day");
        pack.setGroupSize(1);
        pack.setSessionType(SessionType.HALF_DAY);
        pack.setPrice(1200000L);
        pack = pricingPackageRepository.save(pack);

        BookingRequest req = new BookingRequest();
        req.setPackageId(pack.getId());
        req.setCustomerName("Test User");
        req.setPhone("0123456789");
        req.setAddress("Test Address");
        req.setBookingDate(LocalDate.now().plusDays(1));
        req.setSlot(Slot.MORNING);
        req.setTurnstileToken("dummy");
        booking = bookingService.createBooking(req);
    }

    @Test
    void paymentCreatedOnlyWhenBookingConfirmed() {
        assertThat(paymentRepository.findByBookingId(booking.getId())).isEmpty();

        adminBookingController.confirmBooking(booking.getId());

        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payment.getAmount()).isEqualTo(500000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void unpaidToPaid() {
        adminBookingController.confirmBooking(booking.getId());
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();

        paymentService.markAsPaid(payment.getId());

        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void invalidPaymentTransitionRejected() {
        adminBookingController.confirmBooking(booking.getId());
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();

        paymentService.markAsPaid(payment.getId());

        assertThatThrownBy(() -> paymentService.markAsPaid(payment.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already marked as paid");
    }
}
