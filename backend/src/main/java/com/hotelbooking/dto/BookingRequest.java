package com.hotelbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record BookingRequest(
        @NotNull(message = "Hotel is required.") Long hotelId,
        @NotNull(message = "Check-in date is required.") LocalDate checkIn,
        @NotNull(message = "Check-out date is required.") LocalDate checkOut,
        @NotBlank(message = "Guest name is required.")
                @Size(max = 100, message = "Guest name must be 100 characters or fewer.") String guestName,
        @NotBlank(message = "Guest email is required.")
                @Email(message = "Enter a valid email address.")
                @Size(max = 150, message = "Guest email must be 150 characters or fewer.") String guestEmail) {
}
