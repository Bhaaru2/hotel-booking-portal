package com.hotelbooking.service;

import com.hotelbooking.exception.BadRequestException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class StayDateValidator {

    public static final int MAX_STAY_NIGHTS = 30;

    private final Clock clock;

    public StayDateValidator(Clock clock) {
        this.clock = clock;
    }

    public int validate(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new BadRequestException("Both check-in and check-out dates are required.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BadRequestException("Check-out must be after check-in.");
        }
        if (checkIn.isBefore(LocalDate.now(clock))) {
            throw new BadRequestException("Check-in cannot be in the past.");
        }

        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > MAX_STAY_NIGHTS) {
            throw new BadRequestException("A stay cannot be longer than 30 nights.");
        }
        return Math.toIntExact(nights);
    }
}
