package com.photography.backend.booking.controller;

import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.entity.Booking;
import com.photography.backend.booking.entity.BookingStatus;
import com.photography.backend.booking.entity.Slot;
import com.photography.backend.booking.repository.BookingRepository;
import com.photography.backend.booking.service.BookingService;

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
public class AdminBookingControllerTest {

    @Autowired
    private AdminBookingController adminBookingController;
    
    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PricingPackageRepository pricingPackageRepository;

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
}
