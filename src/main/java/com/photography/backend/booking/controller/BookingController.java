package com.photography.backend.booking.controller;

import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.entity.Booking;
import com.photography.backend.booking.service.BookingService;
import com.photography.backend.config.TurnstileService;
import com.photography.backend.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final TurnstileService turnstileService;

    @PostMapping
    public ResponseEntity<Booking> createBooking(@Valid @RequestBody BookingRequest request) {
        if (!turnstileService.verify(request.getTurnstileToken())) {
            throw new BusinessException("Captcha verification failed", "CAPTCHA_FAILED", HttpStatus.BAD_REQUEST);
        }

        Booking booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @GetMapping("/{bookingCode}")
    public ResponseEntity<com.photography.backend.booking.dto.BookingStatusResponse> getBookingStatus(
            @PathVariable String bookingCode) {
        return ResponseEntity.ok(bookingService.getBookingStatus(bookingCode));
    }
}