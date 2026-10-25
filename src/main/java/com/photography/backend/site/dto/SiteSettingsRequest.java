package com.photography.backend.site.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class SiteSettingsRequest {

    @NotBlank(message = "Photographer name is required")
    private String photographerName;

    private String experience;

    private String bio;

    private String facebookUrl;

    private String instagramUrl;

    private String messengerUrl;

    private String heroImageUrl;

    private String heroCloudinaryPublicId;

    private String profileImageUrl;

    private String profileCloudinaryPublicId;

    /**
     * Self-introduction photo (About section) — must reference a photo in the
     * reserved Admin category; null clears the selection.
     */
    private Long profilePhotoId;

    @PositiveOrZero(message = "Deposit amount must not be negative")
    private Long depositAmount;

    @PositiveOrZero(message = "Clients count must not be negative")
    @Max(value = 999999, message = "Clients count is too large")
    private Integer clientsCount;

    @PositiveOrZero(message = "Concepts count must not be negative")
    @Max(value = 999999, message = "Concepts count is too large")
    private Integer conceptsCount;

    @PositiveOrZero(message = "Experience years must not be negative")
    @Max(value = 999, message = "Experience years is too large")
    private Integer experienceYears;

    private String equipment;
}
