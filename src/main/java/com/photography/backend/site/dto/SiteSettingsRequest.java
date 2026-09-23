package com.photography.backend.site.dto;

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

    @PositiveOrZero(message = "Deposit amount must not be negative")
    private Long depositAmount;

    private String equipment;
}
