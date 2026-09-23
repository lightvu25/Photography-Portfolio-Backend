package com.photography.backend.booking.service;

import com.photography.backend.booking.dto.AvailabilityResponse;
import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.dto.SlotAvailabilityStatus;
import com.photography.backend.booking.entity.BlockedSlot;
import com.photography.backend.booking.entity.Booking;
import com.photography.backend.booking.entity.BookingSlot;
import com.photography.backend.booking.entity.BookingStatus;
import com.photography.backend.booking.entity.Slot;
import com.photography.backend.booking.repository.BlockedSlotRepository;
import com.photography.backend.booking.repository.BookingRepository;
import com.photography.backend.booking.repository.BookingSlotRepository;
import com.photography.backend.common.exception.BusinessException;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)

@ActiveProfiles("test")
@Transactional
public class BookingServiceTest {

    @Autowired
    private BookingService bookingService;
    
    @Autowired
    private BookingRepository bookingRepository;
    
    @Autowired
    private BookingSlotRepository bookingSlotRepository;
    
    @Autowired
    private PricingPackageRepository pricingPackageRepository;

    @Autowired
    private BlockedSlotRepository blockedSlotRepository;

    private PricingPackage halfDayPackage;
    private PricingPackage fullDayPackage;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        bookingSlotRepository.deleteAll();
        blockedSlotRepository.deleteAll();
        pricingPackageRepository.deleteAll();

        halfDayPackage = new PricingPackage();
        halfDayPackage.setName("Half Day");
        halfDayPackage.setGroupSize(1);
        halfDayPackage.setSessionType(SessionType.HALF_DAY);
        halfDayPackage.setPrice(1200000L);
        halfDayPackage = pricingPackageRepository.save(halfDayPackage);

        fullDayPackage = new PricingPackage();
        fullDayPackage.setName("Full Day");
        fullDayPackage.setGroupSize(1);
        fullDayPackage.setSessionType(SessionType.FULL_DAY);
        fullDayPackage.setPrice(2000000L);
        fullDayPackage = pricingPackageRepository.save(fullDayPackage);
    }

    private BookingRequest createRequest(Long packageId, Slot slot, LocalDate date) {
        BookingRequest req = new BookingRequest();
        req.setPackageId(packageId);
        req.setCustomerName("Test User");
        req.setPhone("0123456789");
        req.setAddress("Test Address");
        req.setBookingDate(date);
        req.setSlot(slot);
        req.setTurnstileToken("dummy");
        return req;
    }

    @Test
    void createHalfDayMorningBooking() {
        BookingRequest req = createRequest(halfDayPackage.getId(), Slot.MORNING, LocalDate.now().plusDays(1));
        Booking booking = bookingService.createBooking(req);

        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getSlots()).hasSize(1);
        assertThat(booking.getSlots().get(0).getSlot()).isEqualTo(Slot.MORNING);
    }

    @Test
    void createHalfDayAfternoonBooking() {
        BookingRequest req = createRequest(halfDayPackage.getId(), Slot.AFTERNOON, LocalDate.now().plusDays(1));
        Booking booking = bookingService.createBooking(req);

        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getSlots()).hasSize(1);
        assertThat(booking.getSlots().get(0).getSlot()).isEqualTo(Slot.AFTERNOON);
    }

    @Test
    void createFullDayBooking() {
        BookingRequest req = createRequest(fullDayPackage.getId(), null, LocalDate.now().plusDays(1));
        Booking booking = bookingService.createBooking(req);

        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getSlots()).hasSize(2);
        assertThat(booking.getSlots()).extracting(BookingSlot::getSlot).containsExactlyInAnyOrder(Slot.MORNING, Slot.AFTERNOON);
    }

    @Test
    void halfDayCannotUseOccupiedSlot() {
        LocalDate date = LocalDate.now().plusDays(1);
        bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.MORNING, date));

        BookingRequest req2 = createRequest(halfDayPackage.getId(), Slot.MORNING, date);
        assertThatThrownBy(() -> bookingService.createBooking(req2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    void fullDayCannotBeCreatedIfMorningOccupied() {
        LocalDate date = LocalDate.now().plusDays(1);
        bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.MORNING, date));

        BookingRequest req2 = createRequest(fullDayPackage.getId(), null, date);
        assertThatThrownBy(() -> bookingService.createBooking(req2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    void fullDayCannotBeCreatedIfAfternoonOccupied() {
        LocalDate date = LocalDate.now().plusDays(1);
        bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.AFTERNOON, date));

        BookingRequest req2 = createRequest(fullDayPackage.getId(), null, date);
        assertThatThrownBy(() -> bookingService.createBooking(req2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    void pendingBookingExpiresAfter30Minutes() {
        LocalDate date = LocalDate.now().plusDays(1);
        Booking booking = bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.MORNING, date));
        
        // Manually set expiration to past
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(booking);

        bookingService.expirePendingBookings();

        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(updated.getSlots().get(0).getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void expiredBookingReleasesSlot() {
        LocalDate date = LocalDate.now().plusDays(1);
        Booking booking = bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.MORNING, date));
        
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(booking);
        bookingService.expirePendingBookings();

        // Should succeed now
        Booking booking2 = bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.MORNING, date));
        assertThat(booking2.getId()).isNotNull();
    }

    @Test
    void pendingBookingReceives30MinuteExpiration() {
        LocalDateTime before = LocalDateTime.now();
        Booking booking = bookingService.createBooking(
                createRequest(halfDayPackage.getId(), Slot.MORNING, LocalDate.now().plusDays(1)));

        assertThat(booking.getExpiresAt()).isAfter(before.plusMinutes(29));
        assertThat(booking.getExpiresAt()).isBefore(before.plusMinutes(31));
    }

    @Test
    void stalePendingSlotCanBeRebookedImmediately() {
        LocalDate date = LocalDate.now().plusDays(1);
        Booking booking = bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.MORNING, date));

        // Hold elapsed but the scheduled sweep has not run yet
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(booking);

        Booking booking2 = bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.MORNING, date));
        assertThat(booking2.getId()).isNotNull();
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void fullDayFailureLeavesNoPartialData() {
        LocalDate date = LocalDate.now().plusDays(1);
        bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.AFTERNOON, date));

        long bookingsBefore = bookingRepository.count();
        long slotsBefore = bookingSlotRepository.count();

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(fullDayPackage.getId(), null, date)))
                .isInstanceOf(BusinessException.class);

        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
        assertThat(bookingSlotRepository.count()).isEqualTo(slotsBefore);
    }

    @Test
    void blockedSlotPreventsBooking() {
        LocalDate date = LocalDate.now().plusDays(1);
        BlockedSlot blocked = new BlockedSlot();
        blocked.setBlockedDate(date);
        blocked.setSlot(Slot.MORNING);
        blockedSlotRepository.save(blocked);

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.MORNING, date)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("blocked");

        // Other slot is unaffected
        Booking booking = bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.AFTERNOON, date));
        assertThat(booking.getId()).isNotNull();
    }

    @Test
    void fullDayFailsWhenEitherSlotIsBlocked() {
        LocalDate date = LocalDate.now().plusDays(1);
        BlockedSlot blocked = new BlockedSlot();
        blocked.setBlockedDate(date);
        blocked.setSlot(Slot.AFTERNOON);
        blockedSlotRepository.save(blocked);

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(fullDayPackage.getId(), null, date)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("blocked");
    }

    @Test
    void availabilityReflectsBookedBlockedAndAvailable() {
        LocalDate date = LocalDate.now().plusDays(1);
        bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.MORNING, date));

        LocalDate blockedDate = LocalDate.now().plusDays(2);
        BlockedSlot blocked = new BlockedSlot();
        blocked.setBlockedDate(blockedDate);
        blocked.setSlot(Slot.AFTERNOON);
        blockedSlotRepository.save(blocked);

        List<AvailabilityResponse> availability = bookingService.getAvailability(date, blockedDate);

        assertThat(availability).hasSize(2);
        assertThat(availability.get(0).morning()).isEqualTo(SlotAvailabilityStatus.BOOKED);
        assertThat(availability.get(0).afternoon()).isEqualTo(SlotAvailabilityStatus.AVAILABLE);
        assertThat(availability.get(0).fullDayAvailable()).isFalse();

        assertThat(availability.get(1).morning()).isEqualTo(SlotAvailabilityStatus.AVAILABLE);
        assertThat(availability.get(1).afternoon()).isEqualTo(SlotAvailabilityStatus.BLOCKED);
        assertThat(availability.get(1).fullDayAvailable()).isFalse();
    }

    @Test
    void expiredPendingDoesNotAppearBookedInAvailability() {
        LocalDate date = LocalDate.now().plusDays(1);
        Booking booking = bookingService.createBooking(createRequest(halfDayPackage.getId(), Slot.MORNING, date));

        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(booking);

        List<AvailabilityResponse> availability = bookingService.getAvailability(date, date);
        assertThat(availability.get(0).morning()).isEqualTo(SlotAvailabilityStatus.AVAILABLE);
        assertThat(availability.get(0).fullDayAvailable()).isTrue();
    }
}
