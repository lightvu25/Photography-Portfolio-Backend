package com.photography.backend.gallery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photography.backend.gallery.dto.CategoryRequest;
import com.photography.backend.gallery.entity.Category;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import com.photography.backend.gallery.repository.CategoryRepository;
import com.photography.backend.gallery.repository.PhotoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GalleryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        photoRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private Photo photo(PhotoStatus status, int sortOrder) {
        Photo p = new Photo();
        p.setTitle("Photo " + sortOrder);
        p.setCloudinaryPublicId("test/photo" + sortOrder);
        p.setImageUrl("https://res.cloudinary.com/demo/image/upload/v1/test/photo" + sortOrder + ".jpg");
        p.setStatus(status);
        p.setSortOrder(sortOrder);
        return photoRepository.save(p);
    }

    private Category category(String name, int sortOrder) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(name.toLowerCase());
        c.setSortOrder(sortOrder);
        return categoryRepository.save(c);
    }

    @Test
    void publicGalleryReturnsOnlyPublishedInSortOrder() throws Exception {
        photo(PhotoStatus.PUBLISHED, 2);
        photo(PhotoStatus.PUBLISHED, 1);
        photo(PhotoStatus.DRAFT, 0);

        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sortOrder", is(1)))
                .andExpect(jsonPath("$[1].sortOrder", is(2)));
    }

    @Test
    void publicGalleryPhotoIncludesCategories() throws Exception {
        Category portrait = category("Portrait", 0);
        Photo p = photo(PhotoStatus.PUBLISHED, 0);
        p.getCategories().add(portrait);
        photoRepository.save(p);

        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categories", hasSize(1)))
                .andExpect(jsonPath("$[0].categories[0].slug", is("portrait")));
    }

    @Test
    void publicCategoriesEndpoint() throws Exception {
        category("Wedding", 1);
        category("Portrait", 0);

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Portrait")));
    }

    @Test
    void adminPhotoMutationsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/admin/photos")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/photos")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "p.jpg", "image/jpeg", new byte[]{1})))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/photos/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/photos/1/publish")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/photos/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCategoryMutationsRequireAuth() throws Exception {
        CategoryRequest req = new CategoryRequest();
        req.setName("X");
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/categories/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminFeedbackMutationsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/admin/feedbacks")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/feedbacks/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/feedbacks/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicFeedbackCreateAndList() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"An\",\"content\":\"Great!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPublished", is(false)));

        // Unpublished feedback is not public
        mockMvc.perform(get("/api/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void publicFeedbackCreateValidation() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
