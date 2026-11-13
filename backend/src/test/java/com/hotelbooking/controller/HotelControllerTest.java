package com.hotelbooking.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.dto.HotelResponse;
import com.hotelbooking.dto.AlternativeDateResponse;
import com.hotelbooking.dto.HotelAvailabilityResponse;
import com.hotelbooking.service.HotelService;
import com.hotelbooking.dto.HotelDetailResponse;
import com.hotelbooking.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HotelController.class)
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotelService hotelService;

    @Test
    void returnsHotelWithAmenitiesAsJsonArray() throws Exception {
        given(hotelService.getHotels(null)).willReturn(List.of(new HotelResponse(
                1L,
                "Coconut Grove House",
                "Goa",
                "A relaxed coastal retreat.",
                List.of("Wi-Fi", "Garden", "Breakfast"),
                new BigDecimal("8400.00"))));

        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Coconut Grove House"))
                .andExpect(jsonPath("$[0].location").value("Goa"))
                .andExpect(jsonPath("$[0].amenities").isArray())
                .andExpect(jsonPath("$[0].amenities[0]").value("Wi-Fi"))
                .andExpect(jsonPath("$[0].pricePerNight").value(8400.00));
    }

    @Test
    void returnsHotelDetailsAndQuoteForSelectedDates() throws Exception {
        given(hotelService.getHotel(eq(1L), eq(java.time.LocalDate.of(2026, 11, 14)),
                eq(java.time.LocalDate.of(2026, 11, 16))))
                .willReturn(new HotelDetailResponse(
                        1L, "Coconut Grove House", "Goa", "A relaxed coastal retreat.",
                        List.of("Wi-Fi", "Garden"), new BigDecimal("8400.00"),
                        java.time.LocalDate.of(2026, 11, 14), java.time.LocalDate.of(2026, 11, 16),
                        2, true, 3, new BigDecimal("16800.00")));

        mockMvc.perform(get("/api/hotels/1")
                        .param("checkIn", "2026-11-14")
                        .param("checkOut", "2026-11-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.amenities").isArray())
                .andExpect(jsonPath("$.nights").value(2))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.roomsLeft").value(3))
                .andExpect(jsonPath("$.totalAmount").value(16800.00));
    }

    @Test
    void returnsBadRequestWhenOnlyOneStayDateIsProvided() throws Exception {
        given(hotelService.getHotel(eq(1L), eq(java.time.LocalDate.of(2026, 11, 14)), isNull()))
                .willThrow(new BadRequestException("Both check-in and check-out dates are required."));

        mockMvc.perform(get("/api/hotels/1").param("checkIn", "2026-11-14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Both check-in and check-out dates are required."))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void exposesAlternativeDatesOnSearchAndHotelDetails() throws Exception {
        java.time.LocalDate requestedIn = java.time.LocalDate.of(2026, 11, 20);
        java.time.LocalDate requestedOut = java.time.LocalDate.of(2026, 11, 22);
        AlternativeDateResponse alternative = new AlternativeDateResponse(
                java.time.LocalDate.of(2026, 11, 19), java.time.LocalDate.of(2026, 11, 21),
                2, 1, new BigDecimal("16800.00"));
        HotelAvailabilityResponse searchResult = new HotelAvailabilityResponse(
                1L, "Coconut Grove House", "Goa", "A relaxed coastal retreat.",
                List.of("Wi-Fi", "Garden"), new BigDecimal("8400.00"), requestedIn, requestedOut,
                2, false, 0, new BigDecimal("16800.00"), List.of(alternative));
        given(hotelService.searchHotels(null, requestedIn, requestedOut)).willReturn(List.of(searchResult));
        mockMvc.perform(get("/api/hotels").param("checkIn", "2026-11-20").param("checkOut", "2026-11-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alternativeDates[0].checkIn").value("2026-11-19"))
                .andExpect(jsonPath("$[0].alternativeDates[0].nights").value(2));

        given(hotelService.getHotel(eq(1L), eq(requestedIn), eq(requestedOut)))
                .willReturn(new HotelDetailResponse(
                        1L, "Coconut Grove House", "Goa", "A relaxed coastal retreat.",
                        List.of("Wi-Fi", "Garden"), new BigDecimal("8400.00"), requestedIn, requestedOut,
                        2, false, 0, new BigDecimal("16800.00"), List.of(alternative)));
        mockMvc.perform(get("/api/hotels/1").param("checkIn", "2026-11-20").param("checkOut", "2026-11-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternativeDates[0].checkOut").value("2026-11-21"))
                .andExpect(jsonPath("$.alternativeDates[0].totalAmount").value(16800.00));
    }
}
