package com.photography.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.entity.Slot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.is;

@SpringBootTest(properties = {"app.rate-limit.booking=2", "app.rate-limit.login=2"})
@Import(com.photography.backend.config.TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String bookingJson() throws Exception {
        BookingRequest req = new BookingRequest();
        req.setPackageId(1L);
        req.setCustomerName("Test");
        req.setPhone("0123");
        req.setAddress("Addr");
        req.setSlot(Slot.MORNING);
        req.setTurnstileToken("x");
        req.setBookingDate(LocalDate.now().plusDays(1));
        return objectMapper.writeValueAsString(req);
    }

    @Test
    void bookingEndpointRateLimited() throws Exception {
        String body = bookingJson();

        // First two requests pass through the limiter (business result doesn't matter)
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body));
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body));

        // Third request within the same minute is rejected with 429
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error", is("RATE_LIMITED")));
    }

    @Test
    void loginEndpointRateLimited() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"password\":\"x\"}";

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
