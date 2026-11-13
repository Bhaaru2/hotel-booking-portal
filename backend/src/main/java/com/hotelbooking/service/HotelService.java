package com.hotelbooking.service;

import com.hotelbooking.dto.HotelResponse;
import com.hotelbooking.dto.AlternativeDateResponse;
import com.hotelbooking.dto.HotelAvailabilityResponse;
import com.hotelbooking.dto.HotelDetailResponse;
import com.hotelbooking.entity.Hotel;
import com.hotelbooking.entity.RoomInventory;
import com.hotelbooking.exception.BadRequestException;
import com.hotelbooking.repository.HotelRepository;
import com.hotelbooking.repository.RoomInventoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HotelService {

    private static final int ALTERNATIVE_WINDOW_DAYS = 7;
    private static final int MAX_ALTERNATIVE_DATES = 3;

    private final HotelRepository hotelRepository;
    private final RoomInventoryRepository roomInventoryRepository;
    private final StayDateValidator stayDateValidator;

    public HotelService(
            HotelRepository hotelRepository,
            RoomInventoryRepository roomInventoryRepository,
            StayDateValidator stayDateValidator) {
        this.hotelRepository = hotelRepository;
        this.roomInventoryRepository = roomInventoryRepository;
        this.stayDateValidator = stayDateValidator;
    }

    @Transactional(readOnly = true)
    public List<HotelResponse> getHotels(String location) {
        return findHotels(location).stream().map(HotelResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<HotelAvailabilityResponse> searchHotels(String location, LocalDate checkIn, LocalDate checkOut) {
        int nights = stayDateValidator.validate(checkIn, checkOut);
        List<Hotel> hotels = findHotels(location);
        if (hotels.isEmpty()) {
            return List.of();
        }

        LocalDate rangeStart = checkIn.minusDays(ALTERNATIVE_WINDOW_DAYS);
        LocalDate rangeEnd = checkOut.plusDays(ALTERNATIVE_WINDOW_DAYS);
        Map<Long, List<RoomInventory>> inventoryByHotel = roomInventoryRepository.findForHotelsInRange(
                        hotels.stream().map(Hotel::getId).toList(), rangeStart, rangeEnd)
                .stream()
                .collect(Collectors.groupingBy(RoomInventory::getHotelId));

        return hotels.stream()
                .map(hotel -> availabilityResponse(hotel, inventoryByHotel.get(hotel.getId()),
                        checkIn, checkOut, nights))
                .toList();
    }

    @Transactional(readOnly = true)
    public HotelDetailResponse getHotel(Long hotelId, LocalDate checkIn, LocalDate checkOut) {
        if ((checkIn == null) != (checkOut == null)) {
            stayDateValidator.validate(checkIn, checkOut);
        }
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new com.hotelbooking.exception.ResourceNotFoundException(
                        "Hotel " + hotelId + " was not found."));

        if (checkIn == null && checkOut == null) {
            return HotelDetailResponse.detailsOnly(hotel);
        }

        int nights = stayDateValidator.validate(checkIn, checkOut);
        List<RoomInventory> inventory = roomInventoryRepository.findForHotelsInRange(
                List.of(hotelId), checkIn.minusDays(ALTERNATIVE_WINDOW_DAYS),
                checkOut.plusDays(ALTERNATIVE_WINDOW_DAYS));
        HotelAvailabilityResponse availability = availabilityResponse(
                hotel, inventory, checkIn, checkOut, nights);
        return HotelDetailResponse.withAvailability(
                hotel, checkIn, checkOut, nights, availability.available(),
                availability.roomsLeft(), availability.totalAmount(), availability.alternativeDates());
    }

    private List<Hotel> findHotels(String location) {
        return location == null || location.isBlank()
                ? hotelRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                : hotelRepository.findByLocationContainingIgnoreCaseOrderByNameAsc(location.trim());
    }

    private HotelAvailabilityResponse availabilityResponse(
            Hotel hotel,
            List<RoomInventory> inventory,
            LocalDate checkIn,
            LocalDate checkOut,
            int nights) {
        Map<LocalDate, RoomInventory> inventoryByDate = inventory == null ? Map.of() : inventory.stream()
                .collect(Collectors.toMap(RoomInventory::getStayDate, row -> row));
        List<RoomInventory> stayInventory = stayInventory(inventoryByDate, checkIn, nights);
        if (stayInventory.size() != nights) {
            throw new BadRequestException("Selected dates are outside the bookable window.");
        }

        int roomsLeft = stayInventory.stream()
                .mapToInt(room -> room.getTotalRooms() - room.getBookedRooms())
                .min()
                .orElse(0);
        boolean available = stayInventory.stream()
                .allMatch(room -> room.getBookedRooms() < room.getTotalRooms());
        BigDecimal totalAmount = hotel.getPricePerNight().multiply(BigDecimal.valueOf(nights));

        List<AlternativeDateResponse> alternatives = available
                ? List.of()
                : findAlternativeDates(hotel, checkIn, nights, inventoryByDate);

        return HotelAvailabilityResponse.from(
                hotel, checkIn, checkOut, nights, available, roomsLeft, totalAmount, alternatives);
    }

    private List<AlternativeDateResponse> findAlternativeDates(
            Hotel hotel,
            LocalDate requestedCheckIn,
            int nights,
            Map<LocalDate, RoomInventory> inventoryByDate) {
        LocalDate today = stayDateValidator.today();
        return IntStream.rangeClosed(-ALTERNATIVE_WINDOW_DAYS, ALTERNATIVE_WINDOW_DAYS)
                .mapToObj(requestedCheckIn::plusDays)
                .filter(candidate -> !candidate.equals(requestedCheckIn))
                .filter(candidate -> !candidate.isBefore(today))
                .sorted(Comparator
                        .comparingLong((LocalDate candidate) -> Math.abs(
                                ChronoUnit.DAYS.between(requestedCheckIn, candidate)))
                        .thenComparing(Comparator.naturalOrder()))
                .map(candidate -> alternativeFor(hotel, candidate, nights, inventoryByDate))
                .filter(java.util.Objects::nonNull)
                .limit(MAX_ALTERNATIVE_DATES)
                .toList();
    }

    private AlternativeDateResponse alternativeFor(
            Hotel hotel,
            LocalDate checkIn,
            int nights,
            Map<LocalDate, RoomInventory> inventoryByDate) {
        List<RoomInventory> candidateInventory = stayInventory(inventoryByDate, checkIn, nights);
        if (candidateInventory.size() != nights
                || candidateInventory.stream().anyMatch(row -> row.getBookedRooms() >= row.getTotalRooms())) {
            return null;
        }

        HotelAvailabilityResponse availability = availabilityResponse(
                hotel, candidateInventory, checkIn, checkIn.plusDays(nights), nights);
        return AlternativeDateResponse.from(availability);
    }

    private List<RoomInventory> stayInventory(
            Map<LocalDate, RoomInventory> inventoryByDate,
            LocalDate checkIn,
            int nights) {
        List<RoomInventory> stayInventory = new java.util.ArrayList<>(nights);
        for (int night = 0; night < nights; night++) {
            RoomInventory row = inventoryByDate.get(checkIn.plusDays(night));
            if (row == null) {
                return List.of();
            }
            stayInventory.add(row);
        }
        return stayInventory;
    }
}
