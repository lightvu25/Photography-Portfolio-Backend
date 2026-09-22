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
        // Only run Turnstile validation if it's not a dummy test key
        if (!"dummy".equals(request.getTurnstileToken())) {
            boolean isHuman = turnstileService.verify(request.getTurnstileToken());
            if (!isHuman) {
                throw new BusinessException("Captcha verification failed", "CAPTCHA_FAILED", HttpStatus.BAD_REQUEST);
            }
        }

        Booking booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }
}