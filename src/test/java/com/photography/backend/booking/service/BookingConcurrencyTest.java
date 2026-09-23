package com.photography.backend.booking.service;

import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.entity.Booking;
import com.photography.backend.booking.entity.BookingSlot;
import com.photography.backend.booking.entity.BookingStatus;
import com.photography.backend.booking.entity.Slot;
import com.photography.backend.booking.entity.SessionType;
import com.photography.backend.booking.repository.BookingRepository;
import com.photography.backend.booking.repository.BookingSlotRepository;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.pricing.entity.PricingPackage;
import com.photography.backend.pricing.repository.PricingPackageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Non-transactional on purpose: concurrency must be exercised across real
 * database transactions against Testcontainers PostgreSQL.
 */
@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@ActiveProfiles("test")
public class BookingConcurrencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSlotRepository bookingSlotRepository;

    @Autowired
    private PricingPackageRepository pricingPackageRepository;

    private PricingPackage halfDayPackage;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        halfDayPackage = new PricingPackage();
        halfDayPackage.setName("Half Day");
        halfDayPackage.setGroupSize(1);
        halfDayPackage.setSessionType(SessionType.HALF_DAY);
        halfDayPackage.setPrice(1200000L);
        halfDayPackage = pricingPackageRepository.save(halfDayPackage);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        bookingSlotRepository.deleteAll();
        bookingRepository.deleteAll();
        pricingPackageRepository.deleteAll();
    }

    private BookingRequest request(LocalDate date, Slot slot) {
        BookingRequest req = new BookingRequest();
        req.setPackageId(halfDayPackage.getId());
        req.setCustomerName("Concurrent User");
        req.setPhone("0123456789");
        req.setAddress("Test Address");
        req.setBookingDate(date);
        req.setSlot(slot);
        req.setTurnstileToken("dummy");
        return req;
    }

    @Test
    void concurrentSameSlotBookingsOnlyOneSucceeds() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Object> task = () -> {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                return bookingService.createBooking(request(date, Slot.MORNING));
            } catch (BusinessException e) {
                return e;
            }
        };

        try {
            Future<Object> f1 = executor.submit(task);
            Future<Object> f2 = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Object> results = List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));

            long successes = results.stream().filter(Booking.class::isInstance).count();
            long conflicts = results.stream()
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .filter(e -> "BOOKING_CONFLICT".equals(e.getErrorCode()) || "SLOT_BLOCKED".equals(e.getErrorCode()))
                    .count();

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(bookingRepository.findAll()).hasSize(1);
            assertThat(bookingSlotRepository.findAll()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void databaseIndexRejectsDuplicateActiveSlot() {
        LocalDate date = LocalDate.now().plusDays(1);
        Booking first = bookingService.createBooking(request(date, Slot.MORNING));

        // Bypass service-level checks and hit the partial unique index directly
        Booking second = new Booking();
        second.setBookingCode("BK-DUPLICATE-TEST");
        second.setPricingPackage(halfDayPackage);
        second.setCustomerName("Direct Insert");
        second.setPhone("000");
        second.setAddress("x");
        second.setStatus(BookingStatus.PENDING);

        BookingSlot slot = new BookingSlot();
        slot.setBooking(second);
        slot.setBookingDate(date);
        slot.setSlot(Slot.MORNING);
        slot.setStatus(BookingStatus.PENDING);
        second.getSlots().add(slot);

        assertThatThrownBy(() -> bookingRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(first.getId()).isNotNull();
        assertThat(bookingRepository.findAll()).hasSize(1);
    }
}
