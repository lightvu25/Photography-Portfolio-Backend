package com.photography.backend.payment.controller;

import com.photography.backend.booking.controller.AdminBookingController;
import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.entity.Booking;
import com.photography.backend.booking.entity.Slot;
import com.photography.backend.booking.entity.SessionType;
import com.photography.backend.booking.service.BookingService;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.payment.dto.PaymentResponse;
import com.photography.backend.payment.entity.Payment;
import com.photography.backend.payment.entity.PaymentStatus;
import com.photography.backend.payment.repository.PaymentRepository;
import com.photography.backend.pricing.entity.PricingPackage;
import com.photography.backend.pricing.repository.PricingPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@ActiveProfiles("test")
@Transactional
public class AdminPaymentControllerTest {

    @Autowired
    private AdminPaymentController adminPaymentController;

    @Autowired
    private AdminBookingController adminBookingController;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PricingPackageRepository pricingPackageRepository;

    private Booking booking;

    @BeforeEach
    void setUp() {
        PricingPackage pack = new PricingPackage();
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
    void confirmedBookingAppearsInPaymentListWithQrUrl() {
        adminBookingController.confirmBooking(booking.getId());

        List<PaymentResponse> payments = adminPaymentController.getAllPayments().getBody();
        assertThat(payments).hasSize(1);

        PaymentResponse p = payments.get(0);
        assertThat(p.bookingId()).isEqualTo(booking.getId());
        assertThat(p.amount()).isEqualTo(500000L);
        assertThat(p.status()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(p.qrImageUrl()).contains("vietqr.io").contains("500000");
        assertThat(p.qrReference()).isEqualTo(booking.getBookingCode());
    }

    @Test
    void markPaidTransitionsUnpaidToPaid() {
        adminBookingController.confirmBooking(booking.getId());
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();

        PaymentResponse response = adminPaymentController.markAsPaid(payment.getId()).getBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.paidAt()).isNotNull();
    }

    @Test
    void doubleMarkPaidRejected() {
        adminBookingController.confirmBooking(booking.getId());
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();

        adminPaymentController.markAsPaid(payment.getId());

        assertThatThrownBy(() -> adminPaymentController.markAsPaid(payment.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already");
    }

    @Test
    void cancelledBookingDoesNotCreateAnotherPayment() {
        adminBookingController.confirmBooking(booking.getId());
        adminBookingController.cancelBooking(booking.getId());

        assertThat(paymentRepository.findAll()).hasSize(1);
    }
}
