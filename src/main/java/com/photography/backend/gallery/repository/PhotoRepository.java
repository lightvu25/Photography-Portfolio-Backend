package com.photography.backend.gallery.repository;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByStatusOrderBySortOrderAsc(PhotoStatus status);
    List<Photo> findByStatusAndIsFeaturedTrueOrderBySortOrderAsc(PhotoStatus status);

    // Public gallery hides photos that are only categorised under PACKAGE-type
    // categories (pricing cover images). Photos with a PORTFOLIO category — or
    // none — still show, so one photo may serve both purposes.
    String PUBLIC_ONLY = " AND (p.categories IS EMPTY OR EXISTS ("
            + "SELECT 1 FROM p.categories c WHERE c.type = com.photography.backend.gallery.entity.CategoryType.PORTFOLIO))";

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories WHERE p.status = 'PUBLISHED'" + PUBLIC_ONLY + " ORDER BY p.sortOrder")
    List<Photo> findPublishedWithCategories();

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories WHERE p.status = 'PUBLISHED' AND p.isFeatured = true" + PUBLIC_ONLY + " ORDER BY p.sortOrder")
    List<Photo> findFeaturedWithCategories();

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories ORDER BY p.sortOrder")
    List<Photo> findAllWithCategories();
}
