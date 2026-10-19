package com.photography.backend.gallery.entity;
import com.photography.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;
@Entity @Table(name = "photos") @Getter @Setter
public class Photo extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "cloudinary_public_id", nullable = false) private String cloudinaryPublicId;
    /**
     * Display URL — carries the c_crop transformation when a crop is saved.
     * The pristine upload URL lives in {@link #originalImageUrl}.
     */
    @Column(name = "image_url", nullable = false) private String imageUrl;
    /** Untouched Cloudinary secure_url — populated the first time a crop is applied. */
    @Column(name = "original_image_url") private String originalImageUrl;
    @Column(name = "thumbnail_url") private String thumbnailUrl;
    /** Crop rectangle as fractions of the stored image (0..1). All four set or none. */
    @Column(name = "crop_x") private Double cropX;
    @Column(name = "crop_y") private Double cropY;
    @Column(name = "crop_w") private Double cropW;
    @Column(name = "crop_h") private Double cropH;
    @Column(name = "alt_text") private String altText;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PhotoStatus status = PhotoStatus.DRAFT;
    @Column(name = "is_featured", nullable = false) private Boolean isFeatured = false;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder = 0;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "photo_categories", 
               joinColumns = @JoinColumn(name = "photo_id"), 
               inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new HashSet<>();
}