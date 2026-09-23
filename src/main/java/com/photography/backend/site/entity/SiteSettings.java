package com.photography.backend.site.entity;
import com.photography.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name = "site_settings") @Getter @Setter
public class SiteSettings extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "photographer_name", nullable = false) private String photographerName;
    private String experience;
    @Column(columnDefinition = "TEXT") private String bio;
    @Column(name = "facebook_url") private String facebookUrl;
    @Column(name = "instagram_url") private String instagramUrl;
    @Column(name = "messenger_url") private String messengerUrl;
    @Column(name = "hero_image_url") private String heroImageUrl;
    @Column(name = "hero_cloudinary_public_id") private String heroCloudinaryPublicId;
    @Column(name = "profile_image_url") private String profileImageUrl;
    @Column(name = "profile_cloudinary_public_id") private String profileCloudinaryPublicId;
    @Column(name = "deposit_amount", nullable = false) private Long depositAmount = 500000L;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb") private String equipment;
}