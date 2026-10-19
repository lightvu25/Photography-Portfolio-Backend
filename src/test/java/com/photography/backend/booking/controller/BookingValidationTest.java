package com.photography.backend.booking.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@AutoConfigureMockMvc

@ActiveProfiles("test")
public class BookingValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void pastBookingDateRejected() throws Exception {
        BookingRequest req = new BookingRequest();
        req.setPackageId(1L);
        req.setCustomerName("Test");
        req.setPhone("0123");
        req.setAddress("Addr");
        req.setSlot(Slot.MORNING);
        req.setTurnstileToken("dummy");
        req.setBookingDate(LocalDate.now().minusDays(1)); // PAST

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void missingCustomerInfoRejected() throws Exception {
        BookingRequest req = new BookingRequest();
        req.setPackageId(1L);
        req.setBookingDate(LocalDate.now().plusDays(1));
        req.setSlot(Slot.MORNING);
        req.setTurnstileToken("dummy");
        // missing customer name, phone, address

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Regression: malformed bodies (bad enum, bad date, broken JSON) used to
     * fall through to the catch-all handler and return an unexplained 500.
     * They are client errors and must be 400.
     */
    @Test
    void malformedBodyRejectedAs400Not500() throws Exception {
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageId\":1,\"customerName\":\"T\",\"phone\":\"0\",\"address\":\"x\"," +
                        "\"bookingDate\":\"2026-10-25\",\"slot\":\"NOON\",\"turnstileToken\":\"t\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageId\":1,\"customerName\":\"T\",\"phone\":\"0\",\"address\":\"x\"," +
                        "\"bookingDate\":\"25/10/2026\",\"slot\":\"MORNING\",\"turnstileToken\":\"t\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
