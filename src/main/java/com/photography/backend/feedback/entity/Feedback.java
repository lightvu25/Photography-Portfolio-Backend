package com.photography.backend.feedback.entity;
import com.photography.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name = "feedbacks") @Getter @Setter
public class Feedback extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "customer_name", nullable = false) private String customerName;
    @Column(columnDefinition = "TEXT") private String content;
    @Column(name = "cloudinary_public_id") private String cloudinaryPublicId;
    @Column(name = "image_url") private String imageUrl;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder = 0;
    @Column(name = "is_published", nullable = false) private Boolean isPublished = false;
}