package com.photography.backend.booking.controller;

import com.photography.backend.booking.dto.AvailabilityResponse;
import com.photography.backend.booking.service.BookingService;
import com.photography.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private static final long MAX_RANGE_DAYS = 62;

    private final BookingService bookingService;

    @GetMapping
    public ResponseEntity<List<AvailabilityResponse>> getAvailability(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        LocalDate startDate = start != null ? start : LocalDate.now();
        LocalDate endDate = end != null ? end : startDate;

        if (endDate.isBefore(startDate)) {
            throw new BusinessException("End date must not be before start date", "INVALID_RANGE", HttpStatus.BAD_REQUEST);
        }
        if (endDate.toEpochDay() - startDate.toEpochDay() > MAX_RANGE_DAYS) {
            throw new BusinessException("Date range cannot exceed " + MAX_RANGE_DAYS + " days", "INVALID_RANGE", HttpStatus.BAD_REQUEST);
        }

        return ResponseEntity.ok(bookingService.getAvailability(startDate, endDate));
    }
}
