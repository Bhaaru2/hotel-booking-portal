package com.hotelbooking.service;

import com.hotelbooking.dto.HotelResponse;
import com.hotelbooking.dto.HotelAvailabilityResponse;
import com.hotelbooking.dto.HotelDetailResponse;
import com.hotelbooking.entity.Hotel;
import com.hotelbooking.entity.RoomInventory;
import com.hotelbooking.exception.BadRequestException;
import com.hotelbooking.repository.HotelRepository;
import com.hotelbooking.repository.RoomInventoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HotelService {

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
    public List<?> searchHotels(String location, LocalDate checkIn, LocalDate checkOut) {
        int nights = stayDateValidator.validate(checkIn, checkOut);
        List<Hotel> hotels = findHotels(location);
        if (hotels.isEmpty()) {
            return List.of();
        }

        Map<Long, List<RoomInventory>> inventoryByHotel = roomInventoryRepository.findForStay(
                        hotels.stream().map(Hotel::getId).toList(), checkIn, checkOut)
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
        List<RoomInventory> inventory = roomInventoryRepository.findForStay(
                List.of(hotelId), checkIn, checkOut);
        HotelAvailabilityResponse availability = availabilityResponse(
                hotel, inventory, checkIn, checkOut, nights);
        return HotelDetailResponse.withAvailability(
                hotel, checkIn, checkOut, nights, availability.available(),
                availability.roomsLeft(), availability.totalAmount());
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
        if (inventory == null || inventory.size() != nights) {
            throw new BadRequestException("Selected dates are outside the bookable window.");
        }

        int roomsLeft = inventory.stream()
                .mapToInt(room -> room.getTotalRooms() - room.getBookedRooms())
                .min()
                .orElse(0);
        boolean available = inventory.stream()
                .allMatch(room -> room.getBookedRooms() < room.getTotalRooms());
        BigDecimal totalAmount = hotel.getPricePerNight().multiply(BigDecimal.valueOf(nights));

        return HotelAvailabilityResponse.from(
                hotel, checkIn, checkOut, nights, available, roomsLeft, totalAmount);
    }
}
