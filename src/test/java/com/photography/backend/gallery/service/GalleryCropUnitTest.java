package com.photography.backend.gallery.service;

import com.photography.backend.cloudinary.CloudinaryService;
import com.photography.backend.gallery.dto.CategoryRequest;
import com.photography.backend.gallery.dto.PhotoUpdateRequest;
import com.photography.backend.gallery.entity.Category;
import com.photography.backend.gallery.entity.CategoryType;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.repository.CategoryRepository;
import com.photography.backend.gallery.repository.PhotoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
