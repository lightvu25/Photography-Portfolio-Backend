package com.photography.backend.booking.controller;

import com.photography.backend.booking.dto.BlockedSlotRequest;
import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.entity.BlockedSlot;
import com.photography.backend.booking.entity.Slot;
import com.photography.backend.booking.repository.BlockedSlotRepository;
import com.photography.backend.booking.repository.BookingRepository;
import com.photography.backend.booking.service.BookingService;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.booking.entity.SessionType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@ActiveProfiles("test")
@Transactional
public class AdminBlockedSlotControllerTest {

    @Autowired
    private AdminBlockedSlotController adminBlockedSlotController;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BlockedSlotRepository blockedSlotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PricingPackageRepository pricingPackageRepository;

    private PricingPackage pack;

    @BeforeEach
    void setUp() {
        blockedSlotRepository.deleteAll();
        bookingRepository.deleteAll();
        pricingPackageRepository.deleteAll();

        pack = new PricingPackage();
        pack.setName("Half Day");
        pack.setGroupSize(1);
        pack.setSessionType(SessionType.HALF_DAY);
        pack.setPrice(1200000L);
        pack = pricingPackageRepository.save(pack);
    }

    private BlockedSlotRequest blockRequest(LocalDate date, Slot slot) {
        BlockedSlotRequest req = new BlockedSlotRequest();
        req.setBlockedDate(date);
        req.setSlot(slot);
        req.setReason("Holiday");
        return req;
    }

    @Test
    void blockAndUnblockSlot() {
        LocalDate date = LocalDate.now().plusDays(1);
        BlockedSlot blocked = adminBlockedSlotController.blockSlot(blockRequest(date, Slot.MORNING)).getBody();

        assertThat(blocked).isNotNull();
        assertThat(blockedSlotRepository.findByBlockedDateAndSlot(date, Slot.MORNING)).isPresent();

        adminBlockedSlotController.unblockSlot(blocked.getId());
        assertThat(blockedSlotRepository.findByBlockedDateAndSlot(date, Slot.MORNING)).isEmpty();
    }

    @Test
    void duplicateBlockedSlotRejected() {
        LocalDate date = LocalDate.now().plusDays(1);
        adminBlockedSlotController.blockSlot(blockRequest(date, Slot.MORNING));

        assertThatThrownBy(() -> adminBlockedSlotController.blockSlot(blockRequest(date, Slot.MORNING)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already blocked");
    }

    @Test
    void cannotBlockSlotWithActiveBooking() {
        LocalDate date = LocalDate.now().plusDays(1);
        BookingRequest req = new BookingRequest();
        req.setPackageId(pack.getId());
        req.setCustomerName("Test User");
        req.setPhone("0123456789");
        req.setAddress("Test Address");
        req.setBookingDate(date);
        req.setSlot(Slot.MORNING);
        req.setTurnstileToken("dummy");
        bookingService.createBooking(req);

        assertThatThrownBy(() -> adminBlockedSlotController.blockSlot(blockRequest(date, Slot.MORNING)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active booking");

        // Customer booking must remain untouched
        assertThat(bookingRepository.findAll()).hasSize(1);
    }

    @Test
    void listBlockedSlotsInRange() {
        LocalDate date = LocalDate.now().plusDays(1);
        adminBlockedSlotController.blockSlot(blockRequest(date, Slot.MORNING));
        adminBlockedSlotController.blockSlot(blockRequest(date.plusDays(5), Slot.AFTERNOON));

        assertThat(adminBlockedSlotController.getBlockedSlots(date, date.plusDays(1)).getBody()).hasSize(1);
        assertThat(adminBlockedSlotController.getBlockedSlots(date, date.plusDays(5)).getBody()).hasSize(2);
        assertThat(adminBlockedSlotController.getBlockedSlots(null, null).getBody()).hasSize(2);
    }
}
