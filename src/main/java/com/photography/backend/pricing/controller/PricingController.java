package com.photography.backend.pricing.controller;
import com.photography.backend.pricing.entity.PricingPackage;
import com.photography.backend.pricing.repository.PricingPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
@RestController @RequestMapping("/api/pricing") @RequiredArgsConstructor
public class PricingController {
    private final PricingPackageRepository repository;
    @GetMapping
    public ResponseEntity<List<PricingPackage>> getActivePackages() {
        return ResponseEntity.ok(repository.findByIsActiveTrueOrderBySortOrderAsc());
    }
}