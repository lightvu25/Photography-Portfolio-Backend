package com.photography.backend.booking.service;

import com.photography.backend.booking.dto.AvailabilityResponse;
import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.dto.BookingStatusResponse;
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
import com.photography.backend.payment.entity.Payment;
import com.photography.backend.payment.repository.PaymentRepository;
import com.photography.backend.payment.service.PaymentService;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final BlockedSlotRepository blockedSlotRepository;
    private final PricingPackageRepository pricingPackageRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

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
            // Flush inside the guarded block: the unique-slot insert must fail
            // HERE as DataIntegrityViolationException → 409. With plain save()
            // the insert defers to commit, outside this catch, where the
            // failure surfaces as an unhandled 500 instead.
            return bookingRepository.saveAndFlush(booking);
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
        LocalDateTime now = LocalDateTime.now();
        expireStalePendingSlot(date, slot, now);
        if (blockedSlotRepository.findByBlockedDateAndSlot(date, slot).isPresent()) {
            throw new BusinessException("Slot is blocked", "SLOT_BLOCKED", HttpStatus.CONFLICT);
        }
        if (bookingSlotRepository.findActiveSlot(date, slot, now).isPresent()) {
            throw new BusinessException("Slot is already booked", "BOOKING_CONFLICT", HttpStatus.CONFLICT);
        }
    }

    private void expireStalePendingSlot(LocalDate date, Slot slot, LocalDateTime now) {
        bookingSlotRepository.findStalePendingSlot(date, slot, now)
                .map(BookingSlot::getBooking)
                .ifPresent(this::expireBooking);
    }

    private void expireBooking(Booking booking) {
        booking.setStatus(BookingStatus.EXPIRED);
        for (BookingSlot slot : booking.getSlots()) {
            slot.setStatus(BookingStatus.EXPIRED);
        }
        // Flush so the EXPIRED status update hits the database before any new
        // slot insert in the same transaction - otherwise the unique index
        // still sees the row as PENDING when the insert executes.
        bookingRepository.saveAndFlush(booking);
        log.info("Expired booking: {}", booking.getBookingCode());
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getAvailability(LocalDate startDate, LocalDate endDate) {
        LocalDateTime now = LocalDateTime.now();
        List<BookingSlot> activeSlots = bookingSlotRepository.findActiveSlotsBetween(startDate, endDate, now);
        List<BlockedSlot> blockedSlots = blockedSlotRepository.findBetween(startDate, endDate);

        Set<String> booked = activeSlots.stream()
                .map(s -> s.getBookingDate() + ":" + s.getSlot())
                .collect(Collectors.toSet());
        Set<String> blocked = blockedSlots.stream()
                .map(s -> s.getBlockedDate() + ":" + s.getSlot())
                .collect(Collectors.toSet());

        return startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    SlotAvailabilityStatus morning = slotStatus(date, Slot.MORNING, booked, blocked);
                    SlotAvailabilityStatus afternoon = slotStatus(date, Slot.AFTERNOON, booked, blocked);
                    boolean fullDay = morning == SlotAvailabilityStatus.AVAILABLE
                            && afternoon == SlotAvailabilityStatus.AVAILABLE;
                    return new AvailabilityResponse(date, morning, afternoon, fullDay);
                })
                .toList();
    }

    private SlotAvailabilityStatus slotStatus(LocalDate date, Slot slot, Set<String> booked, Set<String> blocked) {
        String key = date + ":" + slot;
        if (blocked.contains(key)) {
            return SlotAvailabilityStatus.BLOCKED;
        }
        if (booked.contains(key)) {
            return SlotAvailabilityStatus.BOOKED;
        }
        return SlotAvailabilityStatus.AVAILABLE;
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

    @Transactional(readOnly = true)
    public BookingStatusResponse getBookingStatus(String bookingCode) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new BusinessException("Booking not found", "NOT_FOUND", HttpStatus.NOT_FOUND));

        List<BookingStatusResponse.SlotInfo> slots = booking.getSlots().stream()
                .map(s -> new BookingStatusResponse.SlotInfo(s.getBookingDate(), s.getSlot()))
                .toList();

        BookingStatusResponse.PaymentInfo payment = paymentRepository.findByBookingId(booking.getId())
                .map(this::toPaymentInfo)
                .orElse(null);

        return new BookingStatusResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getCustomerName(),
                booking.getPricingPackage().getName(),
                slots,
                booking.getExpiresAt(),
                payment
        );
    }

    private BookingStatusResponse.PaymentInfo toPaymentInfo(Payment payment) {
        return new BookingStatusResponse.PaymentInfo(
                payment.getStatus(),
                payment.getAmount(),
                payment.getQrReference(),
                paymentService.generateVietQrUrl(payment)
        );
    }

    private String generateBookingCode() {
        return "BK-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expirePendingBookings() {
        List<Booking> expiredBookings = bookingRepository.findByStatusAndExpiresAtBefore(BookingStatus.PENDING, LocalDateTime.now());
        for (Booking booking : expiredBookings) {
            expireBooking(booking);
        }
    }
}
