package com.photography.backend.gallery.entity;

import com.photography.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType type = CategoryType.PORTFOLIO;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category other)) return false;
        if (id != null && other.id != null) return Objects.equals(id, other.id);
        return slug != null && Objects.equals(slug, other.slug);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(slug);
    }
}
