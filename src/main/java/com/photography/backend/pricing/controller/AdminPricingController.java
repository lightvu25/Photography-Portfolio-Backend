package com.photography.backend.pricing.controller;

import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.gallery.repository.PhotoRepository;
import com.photography.backend.pricing.dto.PricingPackageRequest;
import com.photography.backend.pricing.entity.PricingPackage;
import com.photography.backend.pricing.repository.PricingPackageRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/pricing")
@RequiredArgsConstructor
public class AdminPricingController {

    private final PricingPackageRepository repository;
    private final PhotoRepository photoRepository;

    @GetMapping
    public ResponseEntity<List<PricingPackage>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<PricingPackage> create(@Valid @RequestBody PricingPackageRequest request) {
        PricingPackage pack = new PricingPackage();
        apply(pack, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(pack));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PricingPackage> update(@PathVariable Long id,
                                                 @Valid @RequestBody PricingPackageRequest request) {
        PricingPackage pack = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Package not found", "NOT_FOUND", HttpStatus.NOT_FOUND));
        apply(pack, request);
        return ResponseEntity.ok(repository.save(pack));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        PricingPackage pack = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Package not found", "NOT_FOUND", HttpStatus.NOT_FOUND));
        try {
            repository.delete(pack);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            // Packages referenced by bookings cannot be removed; deactivate instead
            throw new BusinessException("Package is in use by bookings; deactivate it instead",
                    "PACKAGE_IN_USE", HttpStatus.CONFLICT);
        }
        return ResponseEntity.noContent().build();
    }

    private void apply(PricingPackage pack, PricingPackageRequest request) {
        pack.setName(request.getName());
        pack.setGroupSize(request.getGroupSize());
        pack.setSessionType(request.getSessionType());
        pack.setPrice(request.getPrice());
        pack.setDescription(request.getDescription());
        if (request.getSortOrder() != null) {
            pack.setSortOrder(request.getSortOrder());
        }
        if (request.getIsActive() != null) {
            pack.setIsActive(request.getIsActive());
        }
        pack.setCoverPhoto(request.getCoverPhotoId() == null ? null
                : photoRepository.findById(request.getCoverPhotoId())
                        .orElseThrow(() -> new BusinessException(
                                "Cover photo not found: " + request.getCoverPhotoId(),
                                "COVER_PHOTO_NOT_FOUND", HttpStatus.BAD_REQUEST)));
    }
}
