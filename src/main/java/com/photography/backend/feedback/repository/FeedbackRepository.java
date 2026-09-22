package com.photography.backend.feedback.repository;
import com.photography.backend.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByIsPublishedTrueOrderBySortOrderAsc();
}