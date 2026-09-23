package com.photography.backend.feedback.service;

import com.photography.backend.cloudinary.CloudinaryService;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.feedback.dto.FeedbackRequest;
import com.photography.backend.feedback.dto.FeedbackUpdateRequest;
import com.photography.backend.feedback.entity.Feedback;
import com.photography.backend.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    public static final String FEEDBACK_FOLDER = "haibau/feedback";

    private final FeedbackRepository feedbackRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional
    public Feedback createFeedback(FeedbackRequest request) {
        Feedback feedback = new Feedback();
        feedback.setCustomerName(request.getCustomerName());
        feedback.setContent(request.getContent());
        feedback.setIsPublished(false);
        return feedbackRepository.save(feedback);
    }

    @Transactional(readOnly = true)
    public List<Feedback> getPublishedFeedback() {
        return feedbackRepository.findByIsPublishedTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public Feedback updateFeedback(Long id, FeedbackUpdateRequest request) {
        Feedback feedback = getFeedback(id);
        feedback.setCustomerName(request.getCustomerName());
        if (request.getContent() != null) {
            feedback.setContent(request.getContent());
        }
        if (request.getSortOrder() != null) {
            feedback.setSortOrder(request.getSortOrder());
        }
        if (request.getIsPublished() != null) {
            feedback.setIsPublished(request.getIsPublished());
        }
        return feedbackRepository.save(feedback);
    }

    @Transactional
    public Feedback attachImage(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Image file is required", "FILE_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        Feedback feedback = getFeedback(id);

        Map<String, Object> uploadResult;
        try {
            uploadResult = cloudinaryService.uploadFile(file, FEEDBACK_FOLDER);
        } catch (IOException e) {
            log.error("Cloudinary upload failed", e);
            throw new BusinessException("Image upload failed", "UPLOAD_FAILED", HttpStatus.BAD_GATEWAY);
        }

        String imageUrl = (String) uploadResult.get("secure_url");
        String publicId = (String) uploadResult.get("public_id");
        if (imageUrl == null || publicId == null) {
            throw new BusinessException("Image upload failed", "UPLOAD_FAILED", HttpStatus.BAD_GATEWAY);
        }

        String oldPublicId = feedback.getCloudinaryPublicId();
        feedback.setImageUrl(imageUrl);
        feedback.setCloudinaryPublicId(publicId);
        Feedback saved = feedbackRepository.save(feedback);

        // Best-effort cleanup of the replaced asset; the new image is already
        // stored, so a failure here only leaves a deletable orphan, not a broken reference.
        if (oldPublicId != null) {
            try {
                cloudinaryService.deleteFile(oldPublicId);
            } catch (Exception e) {
                log.warn("Could not delete replaced feedback image: {}", oldPublicId, e);
            }
        }
        return saved;
    }

    /**
     * Hard delete. The Cloudinary asset is destroyed first so a failure keeps
     * the database row (and its public_id) intact for a retry.
     */
    @Transactional
    public void deleteFeedback(Long id) {
        Feedback feedback = getFeedback(id);
        if (feedback.getCloudinaryPublicId() != null) {
            cloudinaryService.deleteFile(feedback.getCloudinaryPublicId());
        }
        feedbackRepository.delete(feedback);
    }

    private Feedback getFeedback(Long id) {
        return feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Feedback not found", "NOT_FOUND", HttpStatus.NOT_FOUND));
    }
}
