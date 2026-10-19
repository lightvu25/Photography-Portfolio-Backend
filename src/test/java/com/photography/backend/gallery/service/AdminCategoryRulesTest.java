package com.photography.backend.gallery.service;

import com.photography.backend.cloudinary.CloudinaryService;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.gallery.dto.PhotoUpdateRequest;
import com.photography.backend.gallery.entity.Category;
import com.photography.backend.gallery.entity.CategoryType;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import com.photography.backend.gallery.repository.CategoryRepository;
import com.photography.backend.gallery.repository.PhotoRepository;
import com.photography.backend.site.entity.SiteSettings;
import com.photography.backend.site.service.SiteService;
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
import static org.mockito.Mockito.when;

/**
 * Reserved "Admin" category (slug admin-noi-bo) rules:
 * - exclusive — never combined with other categories;
 * - excluded from public listings even if a mixed row slips into the DB;
 * - the only source for the homepage About self-introduction image;
 * - bulk edits cannot bypass it;
 * - crop bakes a Cloudinary transform without destroying the original URL.
 */
@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@ActiveProfiles("test")
@Transactional
public class AdminCategoryRulesTest {

    @Autowired
    private GalleryService galleryService;
    @Autowired
    private SiteService siteService;
    @Autowired
    private PhotoRepository photoRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @MockBean
    private CloudinaryService cloudinaryService;

    private Category admin;
    private Category portrait;
    private Category packageCat;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        categoryRepository.deleteAll();
        // Flush the deletes now — Hibernate executes inserts before deletes
        // within one flush, so re-seeding 'admin-noi-bo' unflushed collides.
        photoRepository.flush();
        categoryRepository.flush();
        admin = category("Admin", CategoryService.ADMIN_CATEGORY_SLUG, CategoryType.PACKAGE);
        portrait = category("Portrait", "portrait", CategoryType.PORTFOLIO);
        packageCat = category("Package", "goi-1", CategoryType.PACKAGE);
    }

    private Category category(String name, String slug, CategoryType type) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setType(type);
        c.setSortOrder(0);
        return categoryRepository.save(c);
    }

    private Photo createPhoto() throws IOException {
        when(cloudinaryService.uploadFile(any(), eq(GalleryService.PHOTO_FOLDER)))
                .thenReturn(Map.of(
                        "secure_url", "https://res.cloudinary.com/demo/image/upload/v1/haibau/photos/abc.jpg",
                        "public_id", "haibau/photos/abc"));
        return galleryService.uploadPhoto(
                new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                "T", null, null, 0, null);
    }

    private PhotoUpdateRequest cats(Set<Long> ids) {
        PhotoUpdateRequest r = new PhotoUpdateRequest();
        r.setCategoryIds(ids);
        return r;
    }

    // ---------- exclusivity ----------

    @Test
    void adminCategoryCannotMixWithOthers() throws IOException {
        Photo photo = createPhoto();

        assertThatThrownBy(() -> galleryService.updatePhoto(photo.getId(),
                cats(Set.of(admin.getId(), portrait.getId()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Admin");
        assertThatThrownBy(() -> galleryService.updatePhoto(photo.getId(),
                cats(Set.of(admin.getId(), packageCat.getId()))))
                .isInstanceOf(BusinessException.class);

        assertThat(galleryService.getPhoto(photo.getId()).getCategories()).isEmpty();
    }

    @Test
    void adminCategoryAloneIsAllowed() throws IOException {
        Photo photo = createPhoto();
        galleryService.updatePhoto(photo.getId(), cats(Set.of(admin.getId())));

        assertThat(galleryService.getPhoto(photo.getId()).getCategories())
                .extracting(Category::getSlug)
                .containsExactly(CategoryService.ADMIN_CATEGORY_SLUG);
    }

    // ---------- public visibility ----------

    @Test
    void adminOnlyPhotoNeverAppearsPublicly() throws IOException {
        Photo hidden = createPhoto();
        Photo visible = createPhoto();
        hidden.getCategories().add(admin);
        visible.getCategories().add(portrait);
        photoRepository.save(hidden);
        photoRepository.save(visible);
        galleryService.publish(hidden.getId());
        galleryService.publish(visible.getId());

        assertThat(galleryService.getPublishedPhotos())
                .extracting(Photo::getId)
                .containsExactly(visible.getId());
    }

    @Test
    void mixedAdminPhotoExcludedFromPublicEvenIfRowExists() throws IOException {
        // Defence-in-depth: a legacy/buggy row mixing Admin + portfolio must
        // not leak publicly even though writes now reject this state.
        Photo mixed = createPhoto();
        mixed.getCategories().add(admin);
        mixed.getCategories().add(portrait);
        photoRepository.save(mixed);
        galleryService.publish(mixed.getId());

        assertThat(galleryService.getPublishedPhotos()).isEmpty();
    }

    // ---------- bulk operations ----------

    @Test
    void bulkAddAndRemoveCategories() throws IOException {
        Photo a = createPhoto();
        Photo b = createPhoto();
        a.getCategories().add(portrait);
        photoRepository.save(a);

        galleryService.updatePhotoCategories(
                Set.of(a.getId(), b.getId()), Set.of(packageCat.getId()), null);
        assertThat(galleryService.getPhoto(a.getId()).getCategories())
                .extracting(Category::getSlug)
                .containsExactlyInAnyOrder("portrait", "goi-1");
        assertThat(galleryService.getPhoto(b.getId()).getCategories())
                .extracting(Category::getSlug).containsExactly("goi-1");

        galleryService.updatePhotoCategories(
                Set.of(a.getId(), b.getId()), null, Set.of(packageCat.getId()));
        assertThat(galleryService.getPhoto(b.getId()).getCategories()).isEmpty();
        assertThat(galleryService.getPhoto(a.getId()).getCategories())
                .extracting(Category::getSlug).containsExactly("portrait");
    }

    @Test
    void bulkAddAdminReplacesOtherCategories() throws IOException {
        Photo photo = createPhoto();
        photo.getCategories().add(portrait);
        photoRepository.save(photo);

        galleryService.updatePhotoCategories(Set.of(photo.getId()), Set.of(admin.getId()), null);

        assertThat(galleryService.getPhoto(photo.getId()).getCategories())
                .extracting(Category::getSlug)
                .containsExactly(CategoryService.ADMIN_CATEGORY_SLUG);
    }

    @Test
    void bulkAddPublicToAdminPhotoRejectsWholeRequest() throws IOException {
        Photo adminPhoto = createPhoto();
        Photo normal = createPhoto();
        adminPhoto.getCategories().add(admin);
        photoRepository.save(adminPhoto);

        assertThatThrownBy(() -> galleryService.updatePhotoCategories(
                Set.of(adminPhoto.getId(), normal.getId()), Set.of(portrait.getId()), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Admin");

        // Transactional: the normal photo must not be half-updated.
        assertThat(galleryService.getPhoto(normal.getId()).getCategories()).isEmpty();
    }

    @Test
    void bulkRemoveCanDeMixALegacyRow() throws IOException {
        // A pre-V11 mixed row (Admin + portfolio) can be repaired by removing
        // categories — removals can never create a mixed state, so the remove
        // set itself may list Admin alongside others.
        Photo photo = createPhoto();
        photo.getCategories().add(admin);
        photo.getCategories().add(portrait);
        photoRepository.save(photo);

        galleryService.updatePhotoCategories(
                Set.of(photo.getId()), null, Set.of(admin.getId()));
        assertThat(galleryService.getPhoto(photo.getId()).getCategories())
                .extracting(Category::getSlug).containsExactly("portrait");

        galleryService.updatePhotoCategories(
                Set.of(photo.getId()), null, Set.of(admin.getId(), portrait.getId()));
        assertThat(galleryService.getPhoto(photo.getId()).getCategories()).isEmpty();
    }

    @Test
    void bulkRejectsUnknownPhoto() {
        assertThatThrownBy(() -> galleryService.updatePhotoCategories(
                Set.of(99999L), Set.of(portrait.getId()), null))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- crop ----------

    @Test
    void cropBakesTransformAndKeepsOriginal() throws IOException {
        Photo photo = createPhoto();
        String original = photo.getImageUrl();

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setCropX(0.1);
        req.setCropY(0.2);
        req.setCropW(0.5);
        req.setCropH(0.5);
        galleryService.updatePhoto(photo.getId(), req);

        Photo saved = galleryService.getPhoto(photo.getId());
        assertThat(saved.getImageUrl())
                .isEqualTo("https://res.cloudinary.com/demo/image/upload/c_crop,x_0.1,y_0.2,w_0.5,h_0.5/v1/haibau/photos/abc.jpg");
        assertThat(saved.getOriginalImageUrl()).isEqualTo(original);
        assertThat(saved.getThumbnailUrl()).contains("c_crop").contains("w_400,c_fill,q_auto");
        assertThat(saved.getCropW()).isEqualTo(0.5);
    }

    @Test
    void recropUsesOriginalNotStackedTransforms() throws IOException {
        Photo photo = createPhoto();
        String original = photo.getImageUrl();

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setCropX(0.1); req.setCropY(0.1); req.setCropW(0.5); req.setCropH(0.5);
        galleryService.updatePhoto(photo.getId(), req);
        req.setCropX(0.0); req.setCropY(0.0); req.setCropW(0.25); req.setCropH(0.25);
        galleryService.updatePhoto(photo.getId(), req);

        Photo saved = galleryService.getPhoto(photo.getId());
        assertThat(saved.getImageUrl()).contains("c_crop,x_0,y_0,w_0.25,h_0.25");
        assertThat(saved.getImageUrl()).doesNotContain("h_0.5");
        assertThat(saved.getOriginalImageUrl()).isEqualTo(original);
    }

    @Test
    void clearCropRestoresOriginal() throws IOException {
        Photo photo = createPhoto();
        String original = photo.getImageUrl();
        String originalThumb = photo.getThumbnailUrl();

        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setCropX(0.1); req.setCropY(0.1); req.setCropW(0.5); req.setCropH(0.5);
        galleryService.updatePhoto(photo.getId(), req);

        PhotoUpdateRequest clear = new PhotoUpdateRequest();
        clear.setClearCrop(true);
        galleryService.updatePhoto(photo.getId(), clear);

        Photo saved = galleryService.getPhoto(photo.getId());
        assertThat(saved.getImageUrl()).isEqualTo(original);
        assertThat(saved.getThumbnailUrl()).isEqualTo(originalThumb);
        assertThat(saved.getOriginalImageUrl()).isNull();
        assertThat(saved.getCropX()).isNull();
    }

    @Test
    void invalidCropRejected() throws IOException {
        Photo photo = createPhoto();
        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.setCropX(0.8); req.setCropY(0.0); req.setCropW(0.5); req.setCropH(0.5);
        assertThatThrownBy(() -> galleryService.updatePhoto(photo.getId(), req))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- About self-introduction resolution ----------

    @Test
    void aboutPhotoFallsBackToPublishedAdminPhoto() throws IOException {
        Photo adminPhoto = createPhoto();
        Photo publicPhoto = createPhoto();
        adminPhoto.getCategories().add(admin);
        publicPhoto.getCategories().add(portrait);
        photoRepository.save(adminPhoto);
        photoRepository.save(publicPhoto);
        galleryService.publish(adminPhoto.getId());
        galleryService.publish(publicPhoto.getId());

        SiteSettings settings = new SiteSettings();
        siteService.withResolvedAboutPhoto(settings);

        assertThat(settings.getAboutPhoto()).isNotNull();
        assertThat(settings.getAboutPhoto().getId()).isEqualTo(adminPhoto.getId());
    }

    @Test
    void aboutPhotoPrefersExplicitEligibleSelection() throws IOException {
        Photo first = createPhoto();
        Photo chosen = createPhoto();
        first.setSortOrder(1);
        chosen.setSortOrder(2);
        first.getCategories().add(admin);
        chosen.getCategories().add(admin);
        photoRepository.save(first);
        photoRepository.save(chosen);
        galleryService.publish(first.getId());
        galleryService.publish(chosen.getId());

        SiteSettings settings = new SiteSettings();
        settings.setProfilePhoto(chosen);
        siteService.withResolvedAboutPhoto(settings);

        assertThat(settings.getAboutPhoto().getId()).isEqualTo(chosen.getId());
    }

    @Test
    void aboutPhotoIsNullWithoutAdminPhotosAndIgnoresDrafts() throws IOException {
        Photo publicPhoto = createPhoto();
        Photo draftAdmin = createPhoto();
        publicPhoto.getCategories().add(portrait);
        draftAdmin.getCategories().add(admin);
        photoRepository.save(publicPhoto);
        photoRepository.save(draftAdmin);
        galleryService.publish(publicPhoto.getId());
        // draftAdmin stays DRAFT

        SiteSettings settings = new SiteSettings();
        siteService.withResolvedAboutPhoto(settings);
        assertThat(settings.getAboutPhoto()).isNull();
    }
}
