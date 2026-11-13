package com.hotelbooking.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.dto.HotelResponse;
import com.hotelbooking.service.HotelService;
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
}
