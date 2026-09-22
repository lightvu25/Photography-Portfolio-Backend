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
    @Column(name = "image_url", nullable = false) private String imageUrl;
    @Column(name = "thumbnail_url") private String thumbnailUrl;
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