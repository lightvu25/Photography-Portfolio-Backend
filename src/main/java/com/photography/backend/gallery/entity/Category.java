package com.photography.backend.gallery.entity;
import com.photography.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name = "categories") @Getter @Setter
public class Category extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(nullable = false, unique = true) private String slug;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder = 0;
}