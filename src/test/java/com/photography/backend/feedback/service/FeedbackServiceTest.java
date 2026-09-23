package com.photography.backend.feedback.service;

import com.photography.backend.cloudinary.CloudinaryService;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.feedback.dto.FeedbackRequest;
import com.photography.backend.feedback.dto.FeedbackUpdateRequest;
import com.photography.backend.feedback.entity.Feedback;
import com.photography.backend.feedback.repository.FeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@ActiveProfiles("test")
@Transactional
public class FeedbackServiceTest {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @MockBean
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        feedbackRepository.deleteAll();
    }

    private Feedback create(String name, String content) {
        FeedbackRequest req = new FeedbackRequest();
        req.setCustomerName(name);
        req.setContent(content);
        return feedbackService.createFeedback(req);
    }

    @Test
    void publicCreateDefaultsToUnpublished() {
        Feedback feedback = create("An", "Great photos!");

        assertThat(feedback.getId()).isNotNull();
        assertThat(feedback.getIsPublished()).isFalse();
        assertThat(feedbackService.getPublishedFeedback()).isEmpty();
        assertThat(feedbackService.getAllFeedback()).hasSize(1);
    }

    @Test
    void publicApiExposesOnlyPublishedFeedback() {
        Feedback f1 = create("An", "Published one");
        create("Binh", "Hidden one");

        FeedbackUpdateRequest publish = new FeedbackUpdateRequest();
        publish.setCustomerName("An");
        publish.setIsPublished(true);
        feedbackService.updateFeedback(f1.getId(), publish);

        assertThat(feedbackService.getPublishedFeedback())
                .extracting(Feedback::getId)
                .containsExactly(f1.getId());
    }

    @Test
    void adminCanUpdateAndUnpublish() {
        Feedback f = create("An", "Original");

        FeedbackUpdateRequest req = new FeedbackUpdateRequest();
        req.setCustomerName("An Updated");
        req.setContent("Updated content");
        req.setIsPublished(true);
        req.setSortOrder(3);
        Feedback updated = feedbackService.updateFeedback(f.getId(), req);

        assertThat(updated.getCustomerName()).isEqualTo("An Updated");
        assertThat(updated.getContent()).isEqualTo("Updated content");
        assertThat(updated.getIsPublished()).isTrue();
        assertThat(updated.getSortOrder()).isEqualTo(3);

        FeedbackUpdateRequest unpublish = new FeedbackUpdateRequest();
        unpublish.setCustomerName("An Updated");
        unpublish.setIsPublished(false);
        feedbackService.updateFeedback(f.getId(), unpublish);
        assertThat(feedbackService.getPublishedFeedback()).isEmpty();
    }

    @Test
    void attachImageStoresUrlAndPublicId() throws IOException {
        Feedback f = create("An", "Nice");
        when(cloudinaryService.uploadFile(any(), eq(FeedbackService.FEEDBACK_FOLDER)))
                .thenReturn(Map.of(
                        "secure_url", "https://res.cloudinary.com/demo/image/upload/v1/haibau/feedback/img.png",
                        "public_id", "haibau/feedback/img"));

        Feedback updated = feedbackService.attachImage(f.getId(),
                new MockMultipartFile("file", "img.png", "image/png", new byte[]{1}));

        assertThat(updated.getImageUrl()).contains("haibau/feedback/img.png");
        assertThat(updated.getCloudinaryPublicId()).isEqualTo("haibau/feedback/img");
    }

    @Test
    void deletingFeedbackDeletesCloudinaryAsset() throws IOException {
        Feedback f = create("An", "Nice");
        when(cloudinaryService.uploadFile(any(), eq(FeedbackService.FEEDBACK_FOLDER)))
                .thenReturn(Map.of("secure_url", "https://x/img.png", "public_id", "haibau/feedback/img"));
        feedbackService.attachImage(f.getId(),
                new MockMultipartFile("file", "img.png", "image/png", new byte[]{1}));

        feedbackService.deleteFeedback(f.getId());

        verify(cloudinaryService).deleteFile("haibau/feedback/img");
        assertThat(feedbackRepository.findById(f.getId())).isEmpty();
    }

    @Test
    void cloudinaryDeleteFailureKeepsFeedbackRow() throws IOException {
        Feedback f = create("An", "Nice");
        when(cloudinaryService.uploadFile(any(), eq(FeedbackService.FEEDBACK_FOLDER)))
                .thenReturn(Map.of("secure_url", "https://x/img.png", "public_id", "haibau/feedback/img"));
        feedbackService.attachImage(f.getId(),
                new MockMultipartFile("file", "img.png", "image/png", new byte[]{1}));

        doThrow(new BusinessException("Cloudinary deletion failed", "CLOUDINARY_DELETE_FAILED",
                HttpStatus.BAD_GATEWAY))
                .when(cloudinaryService).deleteFile("haibau/feedback/img");

        assertThatThrownBy(() -> feedbackService.deleteFeedback(f.getId()))
                .isInstanceOf(BusinessException.class);
        assertThat(feedbackRepository.findById(f.getId())).isPresent();
    }

    @Test
    void deleteFeedbackWithoutImageSkipsCloudinary() {
        Feedback f = create("An", "Text only");

        feedbackService.deleteFeedback(f.getId());

        verify(cloudinaryService, never()).deleteFile(any());
        assertThat(feedbackRepository.findById(f.getId())).isEmpty();
    }
}
