package com.photography.backend.site.controller;

import com.photography.backend.site.dto.SiteSettingsRequest;
import com.photography.backend.site.entity.SiteSettings;
import com.photography.backend.site.repository.SiteSettingsRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/site")
@RequiredArgsConstructor
public class AdminSiteController {

    private final SiteSettingsRepository repository;

    @GetMapping
    public ResponseEntity<SiteSettings> getSettings() {
        return repository.findAll().stream().findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    @Transactional
    public ResponseEntity<SiteSettings> updateSettings(@Valid @RequestBody SiteSettingsRequest request) {
        SiteSettings settings = repository.findAll().stream().findFirst()
                .orElseGet(SiteSettings::new);

        settings.setPhotographerName(request.getPhotographerName());
        settings.setExperience(request.getExperience());
        settings.setBio(request.getBio());
        settings.setFacebookUrl(request.getFacebookUrl());
        settings.setInstagramUrl(request.getInstagramUrl());
        settings.setMessengerUrl(request.getMessengerUrl());
        settings.setHeroImageUrl(request.getHeroImageUrl());
        settings.setHeroCloudinaryPublicId(request.getHeroCloudinaryPublicId());
        settings.setProfileImageUrl(request.getProfileImageUrl());
        settings.setProfileCloudinaryPublicId(request.getProfileCloudinaryPublicId());
        if (request.getDepositAmount() != null) {
            settings.setDepositAmount(request.getDepositAmount());
        }
        settings.setEquipment(request.getEquipment());

        return ResponseEntity.ok(repository.save(settings));
    }
}
