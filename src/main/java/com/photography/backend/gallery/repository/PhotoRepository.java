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

    // Public gallery hides photos that are only categorised under PACKAGE-type
    // categories (pricing cover images). Photos with a PORTFOLIO category — or
    // none — still show, so one photo may serve both purposes. The reserved
    // 'admin-noi-bo' category is additionally excluded outright: it is for the
    // self-introduction slot only and must never leak via a mixed assignment.
    String PUBLIC_ONLY = " AND (p.categories IS EMPTY OR EXISTS ("
            + "SELECT 1 FROM p.categories c WHERE c.type = com.photography.backend.gallery.entity.CategoryType.PORTFOLIO))"
            + " AND NOT EXISTS (SELECT 1 FROM p.categories r WHERE r.slug = 'admin-noi-bo')";

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
