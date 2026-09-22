package com.photography.backend.pricing.repository;

import com.photography.backend.pricing.entity.PricingPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingPackageRepository extends JpaRepository<PricingPackage, Long> {
    List<PricingPackage> findByIsActiveTrueOrderBySortOrderAsc();
}