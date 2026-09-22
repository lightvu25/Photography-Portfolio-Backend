package com.photography.backend.booking.service;

import com.photography.backend.booking.dto.BookingRequest;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final BlockedSlotRepository blockedSlotRepository;
    private final PricingPackageRepository pricingPackageRepository;

    @Transactional
    public Booking createBooking(BookingRequest request) {
        log.info("Creating booking for date: {}", request.getBookingDate());
        
        PricingPackage pack = pricingPackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new BusinessException("Package not found", "PACKAGE_NOT_FOUND", HttpStatus.NOT_FOUND));

        if (!pack.getIsActive()) {
            throw new BusinessException("Package is no longer active", "PACKAGE_INACTIVE", HttpStatus.BAD_REQUEST);
        }

        if (request.getBookingDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Cannot book a date in the past", "INVALID_DATE", HttpStatus.BAD_REQUEST);
        }

        Booking booking = buildBooking(request, pack);
        
        if (pack.getSessionType() == SessionType.FULL_DAY) {
            checkAvailability(request.getBookingDate(), Slot.MORNING);
            checkAvailability(request.getBookingDate(), Slot.AFTERNOON);
            
            booking.getSlots().add(createSlot(booking, request.getBookingDate(), Slot.MORNING));
            booking.getSlots().add(createSlot(booking, request.getBookingDate(), Slot.AFTERNOON));
        } else {
            if (request.getSlot() == null) {
                throw new BusinessException("Slot is required for HALF_DAY package", "SLOT_REQUIRED", HttpStatus.BAD_REQUEST);
            }
            checkAvailability(request.getBookingDate(), request.getSlot());
            booking.getSlots().add(createSlot(booking, request.getBookingDate(), request.getSlot()));
        }

        try {
            return bookingRepository.save(booking);
        } catch (DataIntegrityViolationException e) {
            log.error("Concurrent booking conflict for {} ", request.getBookingDate());
            throw new BusinessException("The selected time slot is no longer available", "BOOKING_CONFLICT", HttpStatus.CONFLICT);
        }
    }

    private BookingSlot createSlot(Booking booking, LocalDate date, Slot slotType) {
        BookingSlot slot = new BookingSlot();
        slot.setBooking(booking);
        slot.setBookingDate(date);
        slot.setSlot(slotType);
        slot.setStatus(BookingStatus.PENDING);
        return slot;
    }

    private void checkAvailability(LocalDate date, Slot slot) {
        if (blockedSlotRepository.findByBlockedDateAndSlot(date, slot).isPresent()) {
            throw new BusinessException("Slot is blocked", "SLOT_BLOCKED", HttpStatus.CONFLICT);
        }
        if (bookingSlotRepository.findActiveSlot(date, slot).isPresent()) {
            throw new BusinessException("Slot is already booked", "BOOKING_CONFLICT", HttpStatus.CONFLICT);
        }
    }

    private Booking buildBooking(BookingRequest request, PricingPackage pack) {
        Booking booking = new Booking();
        booking.setBookingCode(generateBookingCode());
        booking.setPricingPackage(pack);
        booking.setCustomerName(request.getCustomerName());
        booking.setPhone(request.getPhone());
        booking.setInstagram(request.getInstagram());
        booking.setAddress(request.getAddress());
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return booking;
    }

    private String generateBookingCode() {
        return "BK-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expirePendingBookings() {
        List<Booking> expiredBookings = bookingRepository.findByStatusAndExpiresAtBefore(BookingStatus.PENDING, LocalDateTime.now());
        for (Booking booking : expiredBookings) {
            booking.setStatus(BookingStatus.EXPIRED);
            for (BookingSlot slot : booking.getSlots()) {
                slot.setStatus(BookingStatus.EXPIRED);
            }
            bookingRepository.save(booking);
            log.info("Expired booking: {}", booking.getBookingCode());
        }
    }
}
