package com.photography.backend.booking.controller;

import com.photography.backend.booking.entity.Booking;
import com.photography.backend.booking.entity.BookingSlot;
import com.photography.backend.booking.entity.BookingStatus;
import com.photography.backend.booking.repository.BookingRepository;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<List<Booking>> getAllBookings() {
        return ResponseEntity.ok(bookingRepository.findAll());
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    public ResponseEntity<Booking> confirmBooking(@PathVariable Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Booking not found", "NOT_FOUND", HttpStatus.NOT_FOUND));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("Only PENDING bookings can be confirmed", "INVALID_STATE", HttpStatus.CONFLICT);
        }

        if (booking.getExpiresAt() != null && !booking.getExpiresAt().isAfter(LocalDateTime.now())) {
            booking.setStatus(BookingStatus.EXPIRED);
            for (BookingSlot slot : booking.getSlots()) {
                slot.setStatus(BookingStatus.EXPIRED);
            }
            bookingRepository.save(booking);
            throw new BusinessException("Booking hold has expired", "BOOKING_EXPIRED", HttpStatus.CONFLICT);
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        for (BookingSlot slot : booking.getSlots()) {
            slot.setStatus(BookingStatus.CONFIRMED);
        }
        booking = bookingRepository.save(booking);

        paymentService.createPaymentForBooking(booking);

        return ResponseEntity.ok(booking);
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public ResponseEntity<Booking> rejectBooking(@PathVariable Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Booking not found", "NOT_FOUND", HttpStatus.NOT_FOUND));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("Only PENDING bookings can be rejected", "INVALID_STATE", HttpStatus.CONFLICT);
        }

        booking.setStatus(BookingStatus.REJECTED);
        for (BookingSlot slot : booking.getSlots()) {
            slot.setStatus(BookingStatus.REJECTED);
        }
        return ResponseEntity.ok(bookingRepository.save(booking));
    }
    
    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<Booking> cancelBooking(@PathVariable Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Booking not found", "NOT_FOUND", HttpStatus.NOT_FOUND));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED bookings can be cancelled", "INVALID_STATE", HttpStatus.CONFLICT);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        for (BookingSlot slot : booking.getSlots()) {
            slot.setStatus(BookingStatus.CANCELLED);
        }
        return ResponseEntity.ok(bookingRepository.save(booking));
    }
    
    @PostMapping("/{id}/complete")
    @Transactional
    public ResponseEntity<Booking> completeBooking(@PathVariable Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Booking not found", "NOT_FOUND", HttpStatus.NOT_FOUND));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Only CONFIRMED bookings can be completed", "INVALID_STATE", HttpStatus.CONFLICT);
        }

        booking.setStatus(BookingStatus.COMPLETED);
        for (BookingSlot slot : booking.getSlots()) {
            slot.setStatus(BookingStatus.COMPLETED);
        }
        return ResponseEntity.ok(bookingRepository.save(booking));
    }
}
