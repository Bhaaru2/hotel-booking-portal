package com.hotelbooking.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.dto.BookingRequest;
import com.hotelbooking.dto.BookingResponse;
import com.hotelbooking.entity.BookingStatus;
import com.hotelbooking.exception.ConflictException;
import com.hotelbooking.exception.ResourceNotFoundException;
import com.hotelbooking.service.BookingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
@Import(com.hotelbooking.exception.GlobalExceptionHandler.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void returnsCreatedBookingResponse() throws Exception {
        given(bookingService.createBooking(any(BookingRequest.class))).willReturn(new BookingResponse(
                "HB-7K2M9Q",
                1L,
                "Coconut Grove House",
                "Grace Hopper",
                "grace@example.com",
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 18),
                3,
                new BigDecimal("25200.00"),
                BookingStatus.CONFIRMED,
                LocalDateTime.of(2026, 11, 13, 5, 30)));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("HB-7K2M9Q"))
                .andExpect(jsonPath("$.hotelId").value(1))
                .andExpect(jsonPath("$.hotelName").value("Coconut Grove House"))
                .andExpect(jsonPath("$.guestName").value("Grace Hopper"))
                .andExpect(jsonPath("$.guestEmail").value("grace@example.com"))
                .andExpect(jsonPath("$.checkIn").value("2026-11-15"))
                .andExpect(jsonPath("$.checkOut").value("2026-11-18"))
                .andExpect(jsonPath("$.nights").value(3))
                .andExpect(jsonPath("$.totalAmount").value(25200.00))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-13T05:30:00"));
    }

    @Test
    void returnsFieldErrorsForInvalidBookingFields() throws Exception {
        String invalidBody = """
                {
                  "hotelId": null,
                  "checkIn": "2026-11-15",
                  "checkOut": "2026-11-18",
                  "guestName": "  ",
                  "guestEmail": "not-an-email"
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/bookings"))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(containsInAnyOrder("hotelId", "guestName", "guestEmail")));
    }

    @Test
    void returnsConsistentConflictAndNotFoundResponses() throws Exception {
        given(bookingService.createBooking(any(BookingRequest.class)))
                .willThrow(new ConflictException("No rooms available for the selected dates."));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("No rooms available for the selected dates."))
                .andExpect(jsonPath("$.fieldErrors").isArray());

        given(bookingService.createBooking(any(BookingRequest.class)))
                .willThrow(new ResourceNotFoundException("Hotel 1 was not found."));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Hotel 1 was not found."))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    private String validBody() {
        return """
                {
                  "hotelId": 1,
                  "checkIn": "2026-11-15",
                  "checkOut": "2026-11-18",
                  "guestName": "Grace Hopper",
                  "guestEmail": "grace@example.com"
                }
                """;
    }
}
