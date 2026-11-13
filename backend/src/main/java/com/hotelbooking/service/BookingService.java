package com.hotelbooking.service;

import com.hotelbooking.dto.BookingRequest;
import com.hotelbooking.dto.BookingResponse;
import com.hotelbooking.entity.Booking;
import com.hotelbooking.entity.BookingStatus;
import com.hotelbooking.entity.Hotel;
import com.hotelbooking.entity.RoomInventory;
import com.hotelbooking.exception.BadRequestException;
import com.hotelbooking.exception.ConflictException;
import com.hotelbooking.exception.ResourceNotFoundException;
import com.hotelbooking.repository.BookingRepository;
import com.hotelbooking.repository.HotelRepository;
import com.hotelbooking.repository.RoomInventoryRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int REFERENCE_ATTEMPTS = 5;
    private static final String BOOKING_NOT_FOUND_MESSAGE =
            "Booking not found for the given reference and email";

    private final HotelRepository hotelRepository;
    private final RoomInventoryRepository roomInventoryRepository;
    private final BookingRepository bookingRepository;
    private final StayDateValidator stayDateValidator;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public BookingService(
            HotelRepository hotelRepository,
            RoomInventoryRepository roomInventoryRepository,
            BookingRepository bookingRepository,
            StayDateValidator stayDateValidator,
            Clock clock,
            SecureRandom secureRandom) {
        this.hotelRepository = hotelRepository;
        this.roomInventoryRepository = roomInventoryRepository;
        this.bookingRepository = bookingRepository;
        this.stayDateValidator = stayDateValidator;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Hotel " + request.hotelId() + " was not found."));
        int nights = stayDateValidator.validate(request.checkIn(), request.checkOut());

        // Lock each night in date order so two guests cannot take the last room at the same time.
        List<RoomInventory> inventory = roomInventoryRepository.findForStayForUpdate(
                hotel.getId(), request.checkIn(), request.checkOut());
        if (inventory.size() != nights) {
            throw new BadRequestException("Selected dates are outside the bookable window.");
        }
        if (inventory.stream().anyMatch(room -> room.getBookedRooms() >= room.getTotalRooms())) {
            throw new ConflictException("No rooms available for the selected dates");
        }

        inventory.forEach(RoomInventory::reserveOneRoom);

        BigDecimal totalAmount = hotel.getPricePerNight().multiply(BigDecimal.valueOf(nights));
        String reference = generateUniqueReference();
        Booking booking = new Booking(
                reference,
                hotel,
                request.guestName().trim(),
                request.guestEmail().trim().toLowerCase(Locale.ROOT),
                request.checkIn(),
                request.checkOut(),
                nights,
                totalAmount,
                BookingStatus.CONFIRMED,
                LocalDateTime.now(clock));

        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public BookingResponse findBooking(String reference, String email) {
        Booking booking = bookingRepository.findByReferenceAndGuestEmail(
                        normalizeReference(reference), normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException(BOOKING_NOT_FOUND_MESSAGE));
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse cancelBooking(String reference, String email) {
        Booking booking = bookingRepository.findForCancellation(
                        normalizeReference(reference), normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException(BOOKING_NOT_FOUND_MESSAGE));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return BookingResponse.from(booking).asAlreadyCancelled();
        }
        if (booking.getCheckIn().isBefore(java.time.LocalDate.now(clock))) {
            throw new BadRequestException("Past bookings cannot be cancelled");
        }

        List<RoomInventory> inventory = roomInventoryRepository.findForStayForUpdate(
                booking.getHotel().getId(), booking.getCheckIn(), booking.getCheckOut());
        if (inventory.size() != booking.getNights()) {
            throw new IllegalStateException("Inventory rows are missing for the booking being cancelled.");
        }
        if (inventory.stream().anyMatch(room -> room.getBookedRooms() <= 0)) {
            throw new IllegalStateException("Inventory counts are inconsistent for the booking being cancelled.");
        }

        inventory.forEach(RoomInventory::releaseOneRoom);
        booking.markCancelled();
        return BookingResponse.from(bookingRepository.save(booking));
    }

    private String normalizeReference(String reference) {
        return reference.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateUniqueReference() {
        for (int attempt = 0; attempt < REFERENCE_ATTEMPTS; attempt++) {
            StringBuilder reference = new StringBuilder("HB-");
            for (int character = 0; character < 6; character++) {
                reference.append(REFERENCE_ALPHABET.charAt(
                        secureRandom.nextInt(REFERENCE_ALPHABET.length())));
            }
            String candidate = reference.toString();
            if (!bookingRepository.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique booking reference after five attempts.");
    }
}
