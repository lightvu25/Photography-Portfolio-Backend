package com.photography.backend.gallery.repository;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByStatusOrderBySortOrderAsc(PhotoStatus status);
    List<Photo> findByStatusAndIsFeaturedTrueOrderBySortOrderAsc(PhotoStatus status);

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories WHERE p.status = 'PUBLISHED' ORDER BY p.sortOrder")
    List<Photo> findPublishedWithCategories();

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories WHERE p.status = 'PUBLISHED' AND p.isFeatured = true ORDER BY p.sortOrder")
    List<Photo> findFeaturedWithCategories();

    @Query("SELECT DISTINCT p FROM Photo p LEFT JOIN FETCH p.categories ORDER BY p.sortOrder")
    List<Photo> findAllWithCategories();
}
