package com.hotelbooking.dto;

import com.hotelbooking.entity.Hotel;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public record HotelResponse(
        Long id,
        String name,
        String location,
        String description,
        List<String> amenities,
        BigDecimal pricePerNight) {

    public static HotelResponse from(Hotel hotel) {
        List<String> amenities = Arrays.stream(hotel.getAmenities().split(","))
                .map(String::trim)
                .filter(amenity -> !amenity.isEmpty())
                .toList();

        return new HotelResponse(
                hotel.getId(),
                hotel.getName(),
                hotel.getLocation(),
                hotel.getDescription(),
                amenities,
                hotel.getPricePerNight());
    }
}
