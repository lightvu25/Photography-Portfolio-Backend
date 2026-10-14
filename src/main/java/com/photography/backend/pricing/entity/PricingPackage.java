package com.photography.backend.pricing.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.photography.backend.booking.entity.SessionType;
import com.photography.backend.common.entity.BaseEntity;
import com.photography.backend.gallery.entity.Photo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pricing_packages")
@Getter
@Setter
public class PricingPackage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "group_size", nullable = false)
    private Integer groupSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false)
    private SessionType sessionType;

    @Column(nullable = false)
    private Long price;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Explicit package cover image. The Photo owns the Cloudinary URL/publicId —
     * the package only references it. FK is ON DELETE SET NULL so deleting a
     * photo simply clears the cover.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_photo_id")
    @JsonIgnore
    private Photo coverPhoto;

    /** Serialised as `coverPhotoId`; reading the id never initializes the proxy. */
    public Long getCoverPhotoId() {
        return coverPhoto == null ? null : coverPhoto.getId();
    }

    /** Serialised as `coverImageUrl`; resolved lazily while the session is open. */
    public String getCoverImageUrl() {
        return coverPhoto == null ? null : coverPhoto.getImageUrl();
    }
}