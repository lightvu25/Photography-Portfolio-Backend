package com.photography.backend.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.entity.SessionType;
import com.photography.backend.booking.entity.Slot;
import com.photography.backend.booking.repository.BookingRepository;
import com.photography.backend.config.TurnstileService;
import com.photography.backend.pricing.entity.PricingPackage;
import com.photography.backend.pricing.repository.PricingPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CAPTCHA failure must never create a booking — verification runs before
 * any DB write, so a failed or timed-out verify leaves no partial row.
 */
@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BookingCaptchaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PricingPackageRepository pricingPackageRepository;

    @MockBean
    private TurnstileService turnstileService;

    private PricingPackage pkg;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        pricingPackageRepository.deleteAll();
        pkg = new PricingPackage();
        pkg.setName("Half Day");
        pkg.setGroupSize(1);
        pkg.setSessionType(SessionType.HALF_DAY);
        pkg.setPrice(1200000L);
        pkg = pricingPackageRepository.save(pkg);
    }

    @Test
    void captchaFailureReturns400AndWritesNothing() throws Exception {
        when(turnstileService.verify(anyString())).thenReturn(false);

        BookingRequest req = new BookingRequest();
        req.setPackageId(pkg.getId());
        req.setCustomerName("Test");
        req.setPhone("0900000000");
        req.setBookingDate(LocalDate.now().plusDays(3));
        req.setSlot(Slot.MORNING);
        req.setAddress("Test address");
        req.setTurnstileToken("bad-token");

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CAPTCHA_FAILED"));

        assertEquals(0, bookingRepository.count());
    }

    @Test
    void captchaTimeoutExceptionAlsoWritesNothing() throws Exception {
        // verify() returns false on Cloudflare errors — but even a thrown
        // verify must not leave a partial booking behind.
        when(turnstileService.verify(anyString()))
                .thenThrow(new RuntimeException("simulated verify crash"));

        BookingRequest req = new BookingRequest();
        req.setPackageId(pkg.getId());
        req.setCustomerName("Test");
        req.setPhone("0900000000");
        req.setBookingDate(LocalDate.now().plusDays(3));
        req.setSlot(Slot.MORNING);
        req.setAddress("Test address");
        req.setTurnstileToken("any-token");

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is5xxServerError());

        assertEquals(0, bookingRepository.count());
    }
}
