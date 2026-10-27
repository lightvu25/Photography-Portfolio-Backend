package com.photography.backend.gallery.repository;

import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByStatusOrderBySortOrderAsc(PhotoStatus status);
    List<Photo> findByStatusAndIsFeaturedTrueOrderBySortOrderAsc(PhotoStatus status);

    // Public gallery hides photos that belong to PACKAGE-type categories
    // (pricing cover images), Feedback screenshots, or the reserved 'admin-noi-bo'
    // self-introduction category.
    String PUBLIC_ONLY = " AND (p.categories IS EMPTY OR EXISTS ("
            + "SELECT 1 FROM p.categories c WHERE c.type = com.photography.backend.gallery.entity.CategoryType.PORTFOLIO))"
            + " AND NOT EXISTS (SELECT 1 FROM p.categories r WHERE r.slug = 'admin-noi-bo')"
            + " AND NOT EXISTS (SELECT 1 FROM p.categories pkg WHERE pkg.type = com.photography.backend.gallery.entity.CategoryType.PACKAGE"
            + " OR LOWER(pkg.slug) LIKE 'goi-%' OR LOWER(pkg.name) LIKE 'gói%')"
            + " AND NOT EXISTS (SELECT 1 FROM p.categories f WHERE f.type = com.photography.backend.gallery.entity.CategoryType.FEEDBACK)";

    /** Published photos in the reserved Feedback category — feedback marquee only. */
    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories WHERE p.status = 'PUBLISHED'"
            + " AND EXISTS (SELECT 1 FROM p.categories f WHERE f.type = com.photography.backend.gallery.entity.CategoryType.FEEDBACK)"
            + " ORDER BY p.sortOrder, p.id")
    List<Photo> findPublishedFeedbackPhotos();

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories WHERE p.status = 'PUBLISHED'" + PUBLIC_ONLY + " ORDER BY p.sortOrder")
    List<Photo> findPublishedWithCategories();

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories WHERE p.status = 'PUBLISHED' AND p.isFeatured = true" + PUBLIC_ONLY + " ORDER BY p.sortOrder")
    List<Photo> findFeaturedWithCategories();

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories ORDER BY p.sortOrder")
    List<Photo> findAllWithCategories();

    /** Published photos in a single category — used to resolve the About self-intro image. */
    @Query("SELECT DISTINCT p FROM Photo p JOIN FETCH p.categories c WHERE c.slug = :slug AND p.status = 'PUBLISHED' ORDER BY p.sortOrder")
    List<Photo> findPublishedByCategorySlug(@Param("slug") String slug);

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories WHERE p.id = :id")
    Optional<Photo> findByIdWithCategories(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories WHERE p.id IN :ids")
    List<Photo> findAllByIdInWithCategories(@Param("ids") Collection<Long> ids);
}
