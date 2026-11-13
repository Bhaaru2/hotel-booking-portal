package com.hotelbooking.dto;

import com.hotelbooking.entity.Hotel;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HotelDetailResponse(
        Long id,
        String name,
        String location,
        String description,
        List<String> amenities,
        BigDecimal pricePerNight,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer nights,
        Boolean available,
        Integer roomsLeft,
        BigDecimal totalAmount,
        List<AlternativeDateResponse> alternativeDates) {

    public HotelDetailResponse(
            Long id,
            String name,
            String location,
            String description,
            List<String> amenities,
            BigDecimal pricePerNight,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer nights,
            Boolean available,
            Integer roomsLeft,
            BigDecimal totalAmount) {
        this(id, name, location, description, amenities, pricePerNight, checkIn, checkOut,
                nights, available, roomsLeft, totalAmount, null);
    }

    public static HotelDetailResponse detailsOnly(Hotel hotel) {
        HotelResponse details = HotelResponse.from(hotel);
        return new HotelDetailResponse(
                details.id(), details.name(), details.location(), details.description(),
                details.amenities(), details.pricePerNight(), null, null, null, null, null, null, null);
    }

    public static HotelDetailResponse withAvailability(
            Hotel hotel,
            LocalDate checkIn,
            LocalDate checkOut,
            int nights,
            boolean available,
            int roomsLeft,
            BigDecimal totalAmount,
            List<AlternativeDateResponse> alternativeDates) {
        HotelResponse details = HotelResponse.from(hotel);
        return new HotelDetailResponse(
                details.id(), details.name(), details.location(), details.description(),
                details.amenities(), details.pricePerNight(), checkIn, checkOut, nights,
                available, roomsLeft, totalAmount, alternativeDates);
    }
}
