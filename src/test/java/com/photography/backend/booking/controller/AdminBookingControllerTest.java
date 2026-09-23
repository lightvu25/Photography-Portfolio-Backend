package com.photography.backend.booking.controller;

import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.entity.Booking;
import com.photography.backend.booking.entity.BookingStatus;
import com.photography.backend.booking.entity.Slot;
import com.photography.backend.booking.repository.BookingRepository;
import com.photography.backend.booking.service.BookingService;

import com.photography.backend.common.exception.BusinessException;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)

@ActiveProfiles("test")
@Transactional
public class AdminBookingControllerTest {

    @Autowired
    private AdminBookingController adminBookingController;
    
    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PricingPackageRepository pricingPackageRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private PricingPackage pack;

    @BeforeEach
    void setUp() {
        pack = new PricingPackage();
        pack.setName("Half Day");
        pack.setGroupSize(1);
        pack.setSessionType(SessionType.HALF_DAY);
        pack.setPrice(1200000L);
        pack = pricingPackageRepository.save(pack);
    }

    private Booking createBooking() {
        BookingRequest req = new BookingRequest();
        req.setPackageId(pack.getId());
        req.setCustomerName("Test User");
        req.setPhone("0123456789");
        req.setAddress("Test Address");
        req.setBookingDate(LocalDate.now().plusDays(1));
        req.setSlot(Slot.MORNING);
        req.setTurnstileToken("dummy");
        return bookingService.createBooking(req);
    }

    @Test
    void pendingToConfirmed() {
        Booking b = createBooking();
        adminBookingController.confirmBooking(b.getId());
        
        Booking updated = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void pendingToRejected() {
        Booking b = createBooking();
        adminBookingController.rejectBooking(b.getId());
        
        Booking updated = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.REJECTED);
    }

    @Test
    void confirmedToCancelled() {
        Booking b = createBooking();
        adminBookingController.confirmBooking(b.getId());
        adminBookingController.cancelBooking(b.getId());
        
        Booking updated = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void confirmedToCompleted() {
        Booking b = createBooking();
        adminBookingController.confirmBooking(b.getId());
        adminBookingController.completeBooking(b.getId());
        
        Booking updated = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void invalidStateTransitionsRejected() {
        Booking b = createBooking();
        // Cannot cancel PENDING
        assertThatThrownBy(() -> adminBookingController.cancelBooking(b.getId()))
                .hasMessageContaining("Only CONFIRMED bookings");
                
        // Cannot complete PENDING
        assertThatThrownBy(() -> adminBookingController.completeBooking(b.getId()))
                .hasMessageContaining("Only CONFIRMED bookings");
    }

    @Test
    void confirmationCreatesPayment() {
        Booking b = createBooking();
        adminBookingController.confirmBooking(b.getId());

        assertThat(paymentRepository.findAll())
                .anyMatch(p -> p.getBooking().getId().equals(b.getId()));
    }

    @Test
    void expiredBookingCannotBeConfirmed() {
        Booking b = createBooking();
        b.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(b);

        assertThatThrownBy(() -> adminBookingController.confirmBooking(b.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");

        Booking updated = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    void rejectedBookingCannotBeConfirmed() {
        Booking b = createBooking();
        adminBookingController.rejectBooking(b.getId());

        assertThatThrownBy(() -> adminBookingController.confirmBooking(b.getId()))
                .hasMessageContaining("Only PENDING bookings");
    }

    @Test
    void confirmedBookingCannotBeConfirmedAgain() {
        Booking b = createBooking();
        adminBookingController.confirmBooking(b.getId());

        assertThatThrownBy(() -> adminBookingController.confirmBooking(b.getId()))
                .hasMessageContaining("Only PENDING bookings");
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    void rejectedBookingReleasesSlot() {
        Booking b = createBooking();
        adminBookingController.rejectBooking(b.getId());

        Booking b2 = createBooking();
        assertThat(b2.getId()).isNotNull();
        assertThat(b2.getId()).isNotEqualTo(b.getId());
    }

    @Test
    void cancelledBookingReleasesSlot() {
        Booking b = createBooking();
        adminBookingController.confirmBooking(b.getId());
        adminBookingController.cancelBooking(b.getId());

        Booking b2 = createBooking();
        assertThat(b2.getId()).isNotNull();
        assertThat(b2.getId()).isNotEqualTo(b.getId());
    }
}
