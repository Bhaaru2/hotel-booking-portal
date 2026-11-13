package com.hotelbooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AlternativeDateResponse(
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int roomsLeft,
        BigDecimal totalAmount) {

    public static AlternativeDateResponse from(HotelAvailabilityResponse availability) {
        return new AlternativeDateResponse(
                availability.checkIn(),
                availability.checkOut(),
                availability.nights(),
                availability.roomsLeft(),
                availability.totalAmount());
    }
}
