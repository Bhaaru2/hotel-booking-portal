package com.hotelbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hotelbooking.entity.Booking;
import com.hotelbooking.entity.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        String reference,
        Long hotelId,
        String hotelName,
        String guestName,
        String guestEmail,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        BigDecimal totalAmount,
        BookingStatus status,
        LocalDateTime createdAt,
        Boolean alreadyCancelled) {

    public BookingResponse(
            String reference,
            Long hotelId,
            String hotelName,
            String guestName,
            String guestEmail,
            LocalDate checkIn,
            LocalDate checkOut,
            int nights,
            BigDecimal totalAmount,
            BookingStatus status,
            LocalDateTime createdAt) {
        this(reference, hotelId, hotelName, guestName, guestEmail, checkIn, checkOut,
                nights, totalAmount, status, createdAt, null);
    }

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getReference(),
                booking.getHotel().getId(),
                booking.getHotel().getName(),
                booking.getGuestName(),
                booking.getGuestEmail(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.getNights(),
                booking.getTotalAmount(),
                booking.getStatus(),
                booking.getCreatedAt(),
                null);
    }

    public BookingResponse asAlreadyCancelled() {
        return new BookingResponse(
                reference, hotelId, hotelName, guestName, guestEmail, checkIn, checkOut,
                nights, totalAmount, status, createdAt, true);
    }
}
