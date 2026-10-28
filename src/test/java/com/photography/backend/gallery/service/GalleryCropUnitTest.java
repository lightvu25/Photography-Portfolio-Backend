package com.photography.backend.gallery.service;

import com.photography.backend.cloudinary.CloudinaryService;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.gallery.dto.CategoryRequest;
import com.photography.backend.gallery.dto.PhotoCompleteUploadRequest;
import com.photography.backend.gallery.dto.PhotoUpdateRequest;
import com.photography.backend.gallery.entity.Category;
import com.photography.backend.gallery.entity.CategoryType;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import com.photography.backend.gallery.repository.CategoryRepository;
import com.photography.backend.gallery.repository.PhotoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GalleryCropUnitTest {

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private GalleryService galleryService;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void updatePhotoClampsSubPixelCropFractionsAndFormatsOnePointZero() {
        Photo p = new Photo();
        p.setId(1L);
        p.setTitle("Portrait");
        p.setOriginalImageUrl("https://res.cloudinary.com/demo/image/upload/v1/portfolio/sample.jpg");
        p.setImageUrl("https://res.cloudinary.com/demo/image/upload/v1/portfolio/sample.jpg");

        when(photoRepository.findById(1L)).thenReturn(Optional.of(p));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> inv.getArgument(0));

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setTitle("Portrait");
        // Sub-pixel rounding values from react-easy-crop at zoom=1 ("Gốc")
        req.setCropX(-0.002);
        req.setCropY(0.0);
        req.setCropW(1.004);
        req.setCropH(1.001);

        Photo saved = galleryService.updatePhoto(1L, req);

        assertThat(saved.getCropX()).isEqualTo(0.0);
        assertThat(saved.getCropY()).isEqualTo(0.0);
        assertThat(saved.getCropW()).isEqualTo(1.0);
        assertThat(saved.getCropH()).isEqualTo(1.0);
        assertThat(saved.getImageUrl())
                .contains("/upload/c_crop,x_0,y_0,w_1.0,h_1.0/v1/portfolio/sample.jpg");
        assertThat(saved.getThumbnailUrl())
                .contains("/upload/c_crop,x_0,y_0,w_1.0,h_1.0/w_600,c_limit,q_auto/v1/portfolio/sample.jpg");
    }

    @Test
    void getPortfolioCategoriesExcludesPackageNamedCategoriesAndInfersTypeOnCreate() {
        Category portrait = new Category();
        portrait.setId(1L);
        portrait.setName("Chân dung");
        portrait.setSlug("chan-dung");
        portrait.setType(CategoryType.PORTFOLIO);

        Category goiCaNhan = new Category();
        goiCaNhan.setId(2L);
        goiCaNhan.setName("Gói - Cá nhân");
        goiCaNhan.setSlug("goi-ca-nhan");
        goiCaNhan.setType(CategoryType.PORTFOLIO);

        when(categoryRepository.findAllByTypeOrderBySortOrderAsc(CategoryType.PORTFOLIO))
                .thenReturn(List.of(portrait, goiCaNhan));

        List<Category> publicCats = categoryService.getPortfolioCategories();
        assertThat(publicCats).extracting(Category::getName).containsExactly("Chân dung");

        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryRequest createReq = new CategoryRequest();
        createReq.setName("Gói - Nhóm 2");
        Category created = categoryService.createCategory(createReq);
        assertThat(created.getType()).isEqualTo(CategoryType.PACKAGE);
    }

    @Test
    void completePhotoUpload_persistsVerifiedMetadataAndIsIdempotentOnRetry() {
        PhotoCompleteUploadRequest req = new PhotoCompleteUploadRequest();
        req.setPublicId("haibau/photos/uuid-999");
        req.setVersion(1730150000L);
        req.setSignature("valid-sig");
        req.setFormat("jpg");
        req.setBytes(3_200_000L);
        req.setWidth(4000);
        req.setHeight(3000);
        req.setTitle("Sunset");
        req.setSortOrder(7);
        req.setCategoryIds(Set.of(10L));

        Category portrait = new Category();
        portrait.setId(10L);
        portrait.setName("Portrait");
        portrait.setSlug("portrait");
        portrait.setType(CategoryType.PORTFOLIO);

        when(cloudinaryService.verifyAndBuildSecureUrl(eq(GalleryService.PHOTO_FOLDER), eq(req)))
                .thenReturn("https://res.cloudinary.com/demo/image/upload/v1730150000/haibau/photos/uuid-999.jpg");
        when(photoRepository.findFirstByCloudinaryPublicIdOrderByIdAsc("haibau/photos/uuid-999"))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(portrait));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> {
            Photo saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        Photo created = galleryService.completePhotoUpload(req);
        assertThat(created.getId()).isEqualTo(42L);
        assertThat(created.getCloudinaryPublicId()).isEqualTo("haibau/photos/uuid-999");
        assertThat(created.getImageUrl())
                .isEqualTo("https://res.cloudinary.com/demo/image/upload/v1730150000/haibau/photos/uuid-999.jpg");
        assertThat(created.getThumbnailUrl())
                .isEqualTo("https://res.cloudinary.com/demo/image/upload/w_600,c_limit,q_auto/v1730150000/haibau/photos/uuid-999.jpg");
        assertThat(created.getStatus()).isEqualTo(PhotoStatus.DRAFT);
        assertThat(created.getCategories()).extracting(Category::getId).containsExactly(10L);

        // Retry with the same publicId returns the existing row without inserting a duplicate
        when(photoRepository.findFirstByCloudinaryPublicIdOrderByIdAsc("haibau/photos/uuid-999"))
                .thenReturn(Optional.of(created));
        Photo retryResult = galleryService.completePhotoUpload(req);
        assertThat(retryResult.getId()).isEqualTo(42L);
    }

    @Test
    void completePhotoUpload_rejectsUnknownCategoryWithoutSavingPhoto() {
        PhotoCompleteUploadRequest req = new PhotoCompleteUploadRequest();
        req.setPublicId("haibau/photos/uuid-bad-cat");
        req.setVersion(1730150000L);
        req.setSignature("valid-sig");
        req.setFormat("jpg");
        req.setBytes(1000L);
        req.setWidth(800);
        req.setHeight(600);
        req.setCategoryIds(Set.of(999L));

        when(cloudinaryService.verifyAndBuildSecureUrl(eq(GalleryService.PHOTO_FOLDER), eq(req)))
                .thenReturn("https://res.cloudinary.com/demo/image/upload/v1730150000/haibau/photos/uuid-bad-cat.jpg");
        when(photoRepository.findFirstByCloudinaryPublicIdOrderByIdAsc("haibau/photos/uuid-bad-cat"))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> galleryService.completePhotoUpload(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Category not found");

        verify(photoRepository, never()).save(any(Photo.class));
    }
}
