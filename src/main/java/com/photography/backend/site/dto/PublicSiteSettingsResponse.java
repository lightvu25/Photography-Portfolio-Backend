package com.photography.backend.site.dto;

import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.site.entity.SiteSettings;

/**
 * Sanitized public response for GET /api/site.
 * Exposes only public-facing settings and a minimal AboutPhoto projection
 * without leaking internal Cloudinary publicIds, crop coordinates, original
 * URLs, internal category metadata, or admin-only profilePhoto references.
 */
public record PublicSiteSettingsResponse(
        Long id,
        String photographerName,
        String experience,
        String bio,
        String facebookUrl,
        String instagramUrl,
        String messengerUrl,
        String heroImageUrl,
        String profileImageUrl,
        Long depositAmount,
        String equipment,
        PublicAboutPhoto aboutPhoto
) {
    public record PublicAboutPhoto(
            Long id,
            String title,
            String description,
            String altText,
            String imageUrl
    ) {
        public static PublicAboutPhoto from(Photo photo) {
            if (photo == null) {
                return null;
            }
            return new PublicAboutPhoto(
                    photo.getId(),
                    photo.getTitle(),
                    photo.getDescription(),
                    photo.getAltText(),
                    photo.getImageUrl()
            );
        }
    }

    public static PublicSiteSettingsResponse from(SiteSettings settings) {
        if (settings == null) {
            return null;
        }
        return new PublicSiteSettingsResponse(
                settings.getId(),
                settings.getPhotographerName(),
                settings.getExperience(),
                settings.getBio(),
                settings.getFacebookUrl(),
                settings.getInstagramUrl(),
                settings.getMessengerUrl(),
                settings.getHeroImageUrl(),
                settings.getProfileImageUrl(),
                settings.getDepositAmount(),
                settings.getEquipment(),
                PublicAboutPhoto.from(settings.getAboutPhoto())
        );
    }
}
