package com.photography.backend.booking.repository;

import com.photography.backend.booking.entity.BlockedSlot;
import com.photography.backend.booking.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BlockedSlotRepository extends JpaRepository<BlockedSlot, Long> {
    
    Optional<BlockedSlot> findByBlockedDateAndSlot(LocalDate blockedDate, Slot slot);

    @Query("SELECT b FROM BlockedSlot b WHERE b.blockedDate BETWEEN :startDate AND :endDate")
    List<BlockedSlot> findBetween(LocalDate startDate, LocalDate endDate);
}