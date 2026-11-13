package com.hotelbooking.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.dto.BookingResponse;
import com.hotelbooking.entity.BookingStatus;
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
class BookingLookupCancelControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean BookingService bookingService;

    @Test
    void lookupAndCancelExposeExpectedResponses() throws Exception {
        BookingResponse confirmed = booking(false);
        given(bookingService.findBooking("HB-7K2M9Q", "guest@example.com")).willReturn(confirmed);
        mockMvc.perform(get("/api/bookings/HB-7K2M9Q").param("email", "guest@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reference").value("HB-7K2M9Q"));
        mockMvc.perform(get("/api/bookings/HB-7K2M9Q"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());

        given(bookingService.cancelBooking("HB-7K2M9Q", "guest@example.com")).willReturn(booking(true));
        mockMvc.perform(post("/api/bookings/HB-7K2M9Q/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guest@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.alreadyCancelled").value(true));
        mockMvc.perform(post("/api/bookings/HB-7K2M9Q/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void unknownBookingOnLookupAndCancelUsesSame404Message() throws Exception {
        String message = "Booking not found for the given reference and email";
        given(bookingService.findBooking("HB-UNKNOWN", "guest@example.com"))
                .willThrow(new ResourceNotFoundException(message));
        given(bookingService.cancelBooking("HB-UNKNOWN", "guest@example.com"))
                .willThrow(new ResourceNotFoundException(message));
        mockMvc.perform(get("/api/bookings/HB-UNKNOWN").param("email", "guest@example.com"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(message));
        mockMvc.perform(post("/api/bookings/HB-UNKNOWN/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guest@example.com\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(message));
    }

    private static BookingResponse booking(boolean cancelled) {
        return new BookingResponse("HB-7K2M9Q", 1L, "Test hotel", "Guest", "guest@example.com",
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 18), 3, new BigDecimal("300.00"),
                cancelled ? BookingStatus.CANCELLED : BookingStatus.CONFIRMED,
                LocalDateTime.of(2026, 11, 13, 0, 0), cancelled ? true : null);
    }
}
