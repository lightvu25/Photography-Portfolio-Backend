package com.photography.backend.booking.dto;

import java.time.LocalDate;

public record AvailabilityResponse(
        LocalDate date,
        SlotAvailabilityStatus morning,
        SlotAvailabilityStatus afternoon,
        boolean fullDayAvailable
) {
}
