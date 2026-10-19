package com.photography.backend.site.controller;

import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.repository.PhotoRepository;
import com.photography.backend.gallery.service.CategoryService;
import com.photography.backend.site.dto.SiteSettingsRequest;
import com.photography.backend.site.entity.SiteSettings;
import com.photography.backend.site.repository.SiteSettingsRepository;
import com.photography.backend.site.service.SiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/site")
@RequiredArgsConstructor
public class AdminSiteController {

    private final SiteSettingsRepository repository;
    private final PhotoRepository photoRepository;
    private final SiteService siteService;

    @GetMapping
    public ResponseEntity<SiteSettings> getSettings() {
        return repository.findAll().stream().findFirst()
                .map(s -> ResponseEntity.ok(siteService.withResolvedAboutPhoto(s)))
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
        settings.setProfilePhoto(resolveProfilePhoto(request.getProfilePhotoId()));
        if (request.getDepositAmount() != null) {
            settings.setDepositAmount(request.getDepositAmount());
        }
        settings.setEquipment(request.getEquipment());

        return ResponseEntity.ok(repository.save(settings));
    }

    /**
     * The self-introduction image must be a photo in the reserved Admin
     * category — anything else is rejected so a public gallery photo can never
     * occupy that slot. null clears the selection (auto-pick takes over).
     */
    private Photo resolveProfilePhoto(Long photoId) {
        if (photoId == null) {
            return null;
        }
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new BusinessException("Photo not found", "NOT_FOUND", HttpStatus.NOT_FOUND));
        boolean isAdminPhoto = photo.getCategories().stream()
                .anyMatch(c -> CategoryService.ADMIN_CATEGORY_SLUG.equals(c.getSlug()));
        if (!isAdminPhoto) {
            throw new BusinessException(
                    "Ảnh giới thiệu phải thuộc danh mục Admin",
                    "RESERVED_CATEGORY", HttpStatus.BAD_REQUEST);
        }
        return photo;
    }
}
