package com.photography.backend.site.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photography.backend.site.dto.SiteSettingsRequest;
import com.photography.backend.site.entity.SiteSettings;
import com.photography.backend.site.repository.SiteSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.is;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SiteSettingsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SiteSettingsRepository siteSettingsRepository;

    @Autowired
    private AdminSiteController adminSiteController;

    @BeforeEach
    void setUp() {
        siteSettingsRepository.deleteAll();
    }

    private SiteSettingsRequest sampleRequest() {
        SiteSettingsRequest req = new SiteSettingsRequest();
        req.setPhotographerName("Hai Bau");
        req.setExperience("5 years");
        req.setBio("Wedding photographer");
        req.setInstagramUrl("https://instagram.com/haibau");
        req.setFacebookUrl("https://facebook.com/haibau");
        req.setMessengerUrl("https://m.me/haibau");
        req.setHeroCloudinaryPublicId("haibau/hero/internal-id");
        req.setProfileCloudinaryPublicId("haibau/profile/internal-id");
        req.setDepositAmount(500000L);
        req.setClientsCount(100);
        req.setConceptsCount(10);
        req.setExperienceYears(2);
        req.setEquipment("[\"Sony A7IV\"]");
        return req;
    }

    @Test
    void adminCanCreateAndUpdateSettings() {
        SiteSettings created = adminSiteController.updateSettings(sampleRequest()).getBody();
        assertThat(created).isNotNull();
        assertThat(created.getPhotographerName()).isEqualTo("Hai Bau");
        assertThat(siteSettingsRepository.findAll()).hasSize(1);

        SiteSettingsRequest update = sampleRequest();
        update.setPhotographerName("Hai Bau Updated");
        update.setDepositAmount(600000L);
        adminSiteController.updateSettings(update);

        // Upsert: still a single settings row
        assertThat(siteSettingsRepository.findAll()).hasSize(1);
        SiteSettings saved = siteSettingsRepository.findAll().get(0);
        assertThat(saved.getPhotographerName()).isEqualTo("Hai Bau Updated");
        assertThat(saved.getDepositAmount()).isEqualTo(600000L);
    }

    @Test
    void publicEndpointReturnsSafeSettingsOnly() throws Exception {
        adminSiteController.updateSettings(sampleRequest());

        MvcResult result = mockMvc.perform(get("/api/site"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photographerName", is("Hai Bau")))
                .andExpect(jsonPath("$.instagramUrl", is("https://instagram.com/haibau")))
                .andExpect(jsonPath("$.clientsCount", is(100)))
                .andExpect(jsonPath("$.conceptsCount", is(10)))
                .andExpect(jsonPath("$.experienceYears", is(2)))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("heroCloudinaryPublicId")).isFalse();
        assertThat(body.has("profileCloudinaryPublicId")).isFalse();
        assertThat(body.has("profilePhoto")).isFalse();
        body.fieldNames().forEachRemaining(field ->
                assertThat(field.toLowerCase())
                        .doesNotContain("secret")
                        .doesNotContain("password")
                        .doesNotContain("apikey")
                        .doesNotContain("api_key")
                        .doesNotContain("cloudinarypublicid"));
    }

    @Test
    void publicEndpointReturns404WhenNoSettings() throws Exception {
        mockMvc.perform(get("/api/site"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminSettingsRequireAuthentication() throws Exception {
        mockMvc.perform(put("/api/admin/site")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/site"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankEquipmentIsStoredAsNullInsteadOfFailingOnJsonb() {
        SiteSettingsRequest req = sampleRequest();
        req.setEquipment("");

        adminSiteController.updateSettings(req);

        SiteSettings saved = siteSettingsRepository.findAll().get(0);
        assertThat(saved.getEquipment()).isNull();
    }

    @Test
    void blankPhotographerNameFailsValidation() {
        SiteSettingsRequest req = sampleRequest();
        req.setPhotographerName("");

        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void negativeStatsFailValidationAndNullStatsPreserveExistingValues() {
        adminSiteController.updateSettings(sampleRequest());

        SiteSettingsRequest partialUpdate = sampleRequest();
        partialUpdate.setClientsCount(null);
        partialUpdate.setConceptsCount(null);
        partialUpdate.setExperienceYears(null);
        adminSiteController.updateSettings(partialUpdate);

        SiteSettings saved = siteSettingsRepository.findAll().get(0);
        assertThat(saved.getClientsCount()).isEqualTo(100);
        assertThat(saved.getConceptsCount()).isEqualTo(10);
        assertThat(saved.getExperienceYears()).isEqualTo(2);

        SiteSettingsRequest invalid = sampleRequest();
        invalid.setClientsCount(-1);
        assertThat(validator.validate(invalid)).isNotEmpty();
    }

    @Autowired
    private jakarta.validation.Validator validator;
}
