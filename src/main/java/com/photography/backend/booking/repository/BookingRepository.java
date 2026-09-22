package com.photography.backend.booking.repository;

import com.photography.backend.booking.entity.Booking;
import com.photography.backend.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    
    Optional<Booking> findByBookingCode(String bookingCode);

    List<Booking> findByStatusAndExpiresAtBefore(BookingStatus status, LocalDateTime now);
}
