package com.hotelbooking.dto;

import com.hotelbooking.entity.Hotel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HotelAvailabilityResponse(
        Long id,
        String name,
        String location,
        String description,
        List<String> amenities,
        BigDecimal pricePerNight,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        boolean available,
        int roomsLeft,
        BigDecimal totalAmount) {

    public static HotelAvailabilityResponse from(
            Hotel hotel,
            LocalDate checkIn,
            LocalDate checkOut,
            int nights,
            boolean available,
            int roomsLeft,
            BigDecimal totalAmount) {
        HotelResponse details = HotelResponse.from(hotel);
        return new HotelAvailabilityResponse(
                details.id(),
                details.name(),
                details.location(),
                details.description(),
                details.amenities(),
                details.pricePerNight(),
                checkIn,
                checkOut,
                nights,
                available,
                roomsLeft,
                totalAmount);
    }
}
