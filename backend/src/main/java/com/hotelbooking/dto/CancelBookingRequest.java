package com.hotelbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record CancelBookingRequest(
        @NotBlank(message = "Guest email is required.")
                @Email(message = "Enter a valid email address.")
                @Size(max = 150, message = "Guest email must be 150 characters or fewer.") String email) {

    public CancelBookingRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
