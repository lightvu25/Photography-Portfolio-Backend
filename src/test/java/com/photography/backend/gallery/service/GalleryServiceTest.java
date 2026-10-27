package com.photography.backend.gallery.service;

import com.cloudinary.Cloudinary;
import com.photography.backend.cloudinary.CloudinaryService;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.gallery.dto.CategoryRequest;
import com.photography.backend.gallery.dto.PhotoUpdateRequest;
import com.photography.backend.gallery.entity.Category;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import com.photography.backend.gallery.repository.CategoryRepository;
import com.photography.backend.gallery.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@ActiveProfiles("test")
@Transactional
public class GalleryServiceTest {

    @Autowired
    private GalleryService galleryService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private Cloudinary cloudinary;

    @MockBean
    private CloudinaryService cloudinaryService;

    private Category portrait;
    private Category outdoor;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        categoryRepository.deleteAll();

        portrait = category("Portrait", 0);
        outdoor = category("Outdoor", 1);
    }

    private Category category(String name, int sortOrder) {
        CategoryRequest req = new CategoryRequest();
        req.setName(name);
        req.setSortOrder(sortOrder);
        return categoryService.createCategory(req);
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private void stubUpload() throws IOException {
        when(cloudinaryService.uploadFile(any(), eq(GalleryService.PHOTO_FOLDER)))
                .thenReturn(Map.of(
                        "secure_url", "https://res.cloudinary.com/demo/image/upload/v1/haibau/photos/abc.jpg",
                        "public_id", "haibau/photos/abc"));
    }

    private Photo createPhoto() throws IOException {
        stubUpload();
        return galleryService.uploadPhoto(imageFile(), "Title", "Desc", "Alt", 0, null);
    }

    @Test
    void cloudinaryIsConfiguredFromEnvironment() {
        assertThat(cloudinary.config.cloudName).isEqualTo("dummy");
    }

    @Test
    void uploadStoresUrlAndPublicIdAsDraft() throws IOException {
        Photo photo = createPhoto();

        assertThat(photo.getId()).isNotNull();
        assertThat(photo.getImageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/haibau/photos/abc.jpg");
        assertThat(photo.getCloudinaryPublicId()).isEqualTo("haibau/photos/abc");
        assertThat(photo.getThumbnailUrl()).contains("/upload/w_600,c_limit,q_auto/");
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.DRAFT);
        verify(cloudinaryService).uploadFile(any(), eq(GalleryService.PHOTO_FOLDER));
    }

    @Test
    void emptyFileRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> galleryService.uploadPhoto(empty, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("required");
    }

    @Test
    void draftPhotosHiddenFromPublicGallery() throws IOException {
        Photo draft = createPhoto();
        Photo published = createPhoto();
        galleryService.publish(published.getId());

        assertThat(galleryService.getPublishedPhotos())
                .extracting(Photo::getId)
                .containsExactly(published.getId());
        assertThat(draft.getStatus()).isEqualTo(PhotoStatus.DRAFT);
    }

    @Test
    void publishAndUnpublish() throws IOException {
        Photo photo = createPhoto();

        galleryService.publish(photo.getId());
        assertThat(galleryService.getPhoto(photo.getId()).getStatus()).isEqualTo(PhotoStatus.PUBLISHED);

        galleryService.unpublish(photo.getId());
        assertThat(galleryService.getPhoto(photo.getId()).getStatus()).isEqualTo(PhotoStatus.DRAFT);
        assertThat(galleryService.getPublishedPhotos()).isEmpty();
    }

    @Test
    void updateMetadata() throws IOException {
        Photo photo = createPhoto();

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setTitle("New Title");
        req.setDescription("New Desc");
        req.setIsFeatured(true);
        req.setSortOrder(5);
        galleryService.updatePhoto(photo.getId(), req);

        Photo updated = galleryService.getPhoto(photo.getId());
        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getDescription()).isEqualTo("New Desc");
        assertThat(updated.getIsFeatured()).isTrue();
        assertThat(updated.getSortOrder()).isEqualTo(5);
    }

    @Test
    void hardDeleteRemovesPhotoAndCallsCloudinary() throws IOException {
        Photo photo = createPhoto();
        galleryService.updatePhoto(photo.getId(), categoriesRequest(Set.of(portrait.getId())));

        galleryService.deletePhoto(photo.getId());

        verify(cloudinaryService).deleteFile("haibau/photos/abc");
        assertThat(photoRepository.findById(photo.getId())).isEmpty();
        // category survives; join row gone
        assertThat(categoryRepository.findById(portrait.getId())).isPresent();
    }

    @Test
    void cloudinaryDeleteFailureKeepsPhotoRow() throws IOException {
        Photo photo = createPhoto();
        doThrow(new BusinessException("Cloudinary deletion failed", "CLOUDINARY_DELETE_FAILED",
                org.springframework.http.HttpStatus.BAD_GATEWAY))
                .when(cloudinaryService).deleteFile("haibau/photos/abc");

        assertThatThrownBy(() -> galleryService.deletePhoto(photo.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cloudinary deletion failed");

        // Row retained so the public_id is not lost and the delete can be retried
        assertThat(photoRepository.findById(photo.getId())).isPresent();
    }

    @Test
    void assignMultipleCategoriesAndRemoveThem() throws IOException {
        Photo photo = createPhoto();

        galleryService.updatePhoto(photo.getId(), categoriesRequest(Set.of(portrait.getId(), outdoor.getId())));
        assertThat(galleryService.getPhoto(photo.getId()).getCategories())
                .extracting(Category::getId)
                .containsExactlyInAnyOrder(portrait.getId(), outdoor.getId());

        galleryService.updatePhoto(photo.getId(), categoriesRequest(Set.of(portrait.getId())));
        assertThat(galleryService.getPhoto(photo.getId()).getCategories())
                .extracting(Category::getId)
                .containsExactly(portrait.getId());
    }

    @Test
    void duplicateCategoryAssignmentIsIdempotent() throws IOException {
        Photo photo = createPhoto();

        galleryService.updatePhoto(photo.getId(), categoriesRequest(Set.of(portrait.getId())));
        galleryService.updatePhoto(photo.getId(), categoriesRequest(Set.of(portrait.getId())));

        Photo reloaded = galleryService.getPhoto(photo.getId());
        assertThat(reloaded.getCategories()).hasSize(1);
    }

    @Test
    void publicPhotoExposesCategories() throws IOException {
        Photo photo = createPhoto();
        galleryService.updatePhoto(photo.getId(), categoriesRequest(Set.of(portrait.getId(), outdoor.getId())));
        galleryService.publish(photo.getId());

        Photo pub = galleryService.getPublishedPhotos().get(0);
        assertThat(pub.getCategories())
                .extracting(Category::getSlug)
                .containsExactlyInAnyOrder("portrait", "outdoor");
    }

    @Test
    void unknownCategoryIdRejected() throws IOException {
        Photo photo = createPhoto();
        assertThatThrownBy(() -> galleryService.updatePhoto(photo.getId(), categoriesRequest(Set.of(99999L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void categoryCreateUpdateDelete() {
        CategoryRequest req = new CategoryRequest();
        req.setName("Wedding Photos");
        Category created = categoryService.createCategory(req);
        assertThat(created.getSlug()).isEqualTo("wedding-photos");

        CategoryRequest update = new CategoryRequest();
        update.setName("Weddings");
        update.setSlug("weddings");
        categoryService.updateCategory(created.getId(), update);
        assertThat(categoryRepository.findById(created.getId()).orElseThrow().getSlug()).isEqualTo("weddings");

        categoryService.deleteCategory(created.getId());
        assertThat(categoryRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void duplicateSlugRejected() {
        CategoryRequest req = new CategoryRequest();
        req.setName("Portrait");
        assertThatThrownBy(() -> categoryService.createCategory(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("slug");
    }

    private PhotoUpdateRequest categoriesRequest(Set<Long> ids) {
        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setCategoryIds(ids);
        return req;
    }
}
