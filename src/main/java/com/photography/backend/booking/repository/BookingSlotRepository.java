package com.photography.backend.booking.repository;

import com.photography.backend.booking.entity.BookingSlot;
import com.photography.backend.booking.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, Long> {
    
    @Query("SELECT s FROM BookingSlot s WHERE s.bookingDate = :date AND s.slot = :slot AND s.status IN ('PENDING', 'CONFIRMED')")
    Optional<BookingSlot> findActiveSlot(LocalDate date, Slot slot);

    @Query("SELECT s FROM BookingSlot s WHERE s.bookingDate BETWEEN :startDate AND :endDate AND s.status IN ('PENDING', 'CONFIRMED')")
    List<BookingSlot> findActiveSlotsBetween(LocalDate startDate, LocalDate endDate);
}
