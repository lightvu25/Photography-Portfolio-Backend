package com.photography.backend.booking.repository;

import com.photography.backend.booking.entity.BookingSlot;
import com.photography.backend.booking.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, Long> {
    
    @Query("SELECT s FROM BookingSlot s WHERE s.bookingDate = :date AND s.slot = :slot " +
            "AND (s.status = 'CONFIRMED' OR (s.status = 'PENDING' AND s.booking.expiresAt > :now))")
    Optional<BookingSlot> findActiveSlot(LocalDate date, Slot slot, LocalDateTime now);

    @Query("SELECT s FROM BookingSlot s WHERE s.bookingDate BETWEEN :startDate AND :endDate " +
            "AND (s.status = 'CONFIRMED' OR (s.status = 'PENDING' AND s.booking.expiresAt > :now))")
    List<BookingSlot> findActiveSlotsBetween(LocalDate startDate, LocalDate endDate, LocalDateTime now);

    @Query("SELECT s FROM BookingSlot s WHERE s.bookingDate = :date AND s.slot = :slot " +
            "AND s.status = 'PENDING' AND s.booking.expiresAt <= :now")
    Optional<BookingSlot> findStalePendingSlot(LocalDate date, Slot slot, LocalDateTime now);
}
