package com.photography.backend.gallery.repository;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByStatusOrderBySortOrderAsc(PhotoStatus status);
    List<Photo> findByStatusAndIsFeaturedTrueOrderBySortOrderAsc(PhotoStatus status);
}