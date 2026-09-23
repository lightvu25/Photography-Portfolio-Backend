package com.photography.backend.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photography.backend.booking.dto.BookingRequest;
import com.photography.backend.booking.entity.BlockedSlot;
import com.photography.backend.booking.entity.Slot;
import com.photography.backend.booking.entity.SessionType;
import com.photography.backend.booking.repository.BlockedSlotRepository;
import com.photography.backend.booking.repository.BookingRepository;
import com.photography.backend.booking.repository.BookingSlotRepository;
import com.photography.backend.booking.service.BookingService;
import com.photography.backend.pricing.entity.PricingPackage;
import com.photography.backend.pricing.repository.PricingPackageRepository;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(com.photography.backend.config.TestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BookingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PricingPackageRepository pricingPackageRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSlotRepository bookingSlotRepository;

    @Autowired
    private BlockedSlotRepository blockedSlotRepository;

    @Autowired
    private BookingService bookingService;

    private PricingPackage halfDayPackage;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        halfDayPackage = new PricingPackage();
        halfDayPackage.setName("Half Day");
        halfDayPackage.setGroupSize(1);
        halfDayPackage.setSessionType(SessionType.HALF_DAY);
        halfDayPackage.setPrice(1200000L);
        halfDayPackage = pricingPackageRepository.save(halfDayPackage);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        bookingSlotRepository.deleteAll();
        bookingRepository.deleteAll();
        blockedSlotRepository.deleteAll();
        pricingPackageRepository.deleteAll();
    }

    private BookingRequest request(LocalDate date, Slot slot) {
        BookingRequest req = new BookingRequest();
        req.setPackageId(halfDayPackage.getId());
        req.setCustomerName("Api User");
        req.setPhone("0123456789");
        req.setAddress("Test Address");
        req.setBookingDate(date);
        req.setSlot(slot);
        req.setTurnstileToken("dummy");
        return req;
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void createBookingReturns201AndDuplicateReturns409() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(date, Slot.MORNING))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(date, Slot.MORNING))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("BOOKING_CONFLICT")));
    }

    @Test
    void bookingOnBlockedSlotReturns409() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        BlockedSlot blocked = new BlockedSlot();
        blocked.setBlockedDate(date);
        blocked.setSlot(Slot.MORNING);
        blockedSlotRepository.save(blocked);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(date, Slot.MORNING))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("SLOT_BLOCKED")));
    }

    @Test
    void availabilityEndpointReportsSlotStatuses() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(date, Slot.MORNING))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/availability")
                        .param("start", date.toString())
                        .param("end", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date", is(date.toString())))
                .andExpect(jsonPath("$[0].morning", is("BOOKED")))
                .andExpect(jsonPath("$[0].afternoon", is("AVAILABLE")))
                .andExpect(jsonPath("$[0].fullDayAvailable", is(false)));
    }

    @Test
    void availabilityRejectsInvertedRange() throws Exception {
        LocalDate date = LocalDate.now().plusDays(2);

        mockMvc.perform(get("/api/availability")
                        .param("start", date.toString())
                        .param("end", date.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/blocked-slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockedDate\":\"" + LocalDate.now().plusDays(1) + "\",\"slot\":\"MORNING\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicBookingStatusLookup() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        com.photography.backend.booking.entity.Booking booking =
                bookingService.createBooking(request(date, Slot.MORNING));

        mockMvc.perform(get("/api/bookings/" + booking.getBookingCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode", is(booking.getBookingCode())))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.slots[0].slot", is("MORNING")))
                .andExpect(jsonPath("$.slots[0].bookingDate", is(date.toString())));
    }

    @Test
    void unknownBookingCodeReturns404() throws Exception {
        mockMvc.perform(get("/api/bookings/BK-NONEXISTENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));
    }
}
