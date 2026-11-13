package com.hotelbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hotelbooking.dto.HotelAvailabilityResponse;
import com.hotelbooking.dto.HotelDetailResponse;
import com.hotelbooking.entity.Hotel;
import com.hotelbooking.entity.RoomInventory;
import com.hotelbooking.exception.BadRequestException;
import com.hotelbooking.repository.HotelRepository;
import com.hotelbooking.repository.RoomInventoryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final LocalDate CHECK_IN = TODAY.plusDays(1);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(3);

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomInventoryRepository roomInventoryRepository;

    private HotelService hotelService;
    private Hotel hotel;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-11-13T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
        hotelService = new HotelService(
                hotelRepository,
                roomInventoryRepository,
                new StayDateValidator(fixedClock));
        hotel = hotel(1L, "Coconut Grove House", "Goa", "8400.00", 5);
    }

    @Test
    void calculatesTotalForStayUsingBigDecimal() {
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomInventoryRepository.findForHotelsInRange(List.of(1L), rangeStart(CHECK_IN), rangeEnd(CHECK_OUT)))
                .willReturn(availableInventory(CHECK_IN, 3, 5, 0));

        HotelDetailResponse response = hotelService.getHotel(1L, CHECK_IN, CHECK_OUT);

        assertThat(response.nights()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("25200.00");
    }

    @Test
    void rejectsCheckOutBeforeOrEqualToCheckIn() {
        StayDateValidator validator = new StayDateValidator(fixedClock());

        assertThatThrownBy(() -> validator.validate(CHECK_IN, CHECK_IN.minusDays(1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Check-out must be after check-in.");
        assertThatThrownBy(() -> validator.validate(CHECK_IN, CHECK_IN))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Check-out must be after check-in.");
    }

    @Test
    void rejectsPastCheckInButAllowsToday() {
        StayDateValidator validator = new StayDateValidator(fixedClock());

        assertThatThrownBy(() -> validator.validate(TODAY.minusDays(1), TODAY))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Check-in cannot be in the past.");
        assertThat(validator.validate(TODAY, TODAY.plusDays(1))).isEqualTo(1);
    }

    @Test
    void rejectsStaysLongerThanThirtyNights() {
        assertThatThrownBy(() -> new StayDateValidator(fixedClock())
                        .validate(CHECK_IN, CHECK_IN.plusDays(31)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A stay cannot be longer than 30 nights.");
    }

    @Test
    void rejectsAStayWhenOneOfItsNightsIsMissingFromInventory() {
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomInventoryRepository.findForHotelsInRange(List.of(1L), rangeStart(CHECK_IN), rangeEnd(CHECK_OUT)))
                .willReturn(availableInventory(CHECK_IN, 2, 5, 0));

        assertThatThrownBy(() -> hotelService.getHotel(1L, CHECK_IN, CHECK_OUT))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Selected dates are outside the bookable window.");
    }

    @Test
    void reportsAvailableWhenEveryNightHasCapacityAndUsesMinimumRoomsLeft() {
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomInventoryRepository.findForHotelsInRange(List.of(1L), rangeStart(CHECK_IN), rangeEnd(CHECK_OUT)))
                .willReturn(List.of(
                        inventory(hotel, CHECK_IN, 5, 1),
                        inventory(hotel, CHECK_IN.plusDays(1), 5, 3),
                        inventory(hotel, CHECK_IN.plusDays(2), 5, 2)));

        HotelDetailResponse response = hotelService.getHotel(1L, CHECK_IN, CHECK_OUT);

        assertThat(response.available()).isTrue();
        assertThat(response.roomsLeft()).isEqualTo(2);
        assertThat(response.alternativeDates()).isEmpty();
        verify(roomInventoryRepository).findForHotelsInRange(
                List.of(1L), rangeStart(CHECK_IN), rangeEnd(CHECK_OUT));
    }

    @Test
    void reportsUnavailableWhenAnySingleNightIsFull() {
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomInventoryRepository.findForHotelsInRange(List.of(1L), rangeStart(CHECK_IN), rangeEnd(CHECK_OUT)))
                .willReturn(List.of(
                        inventory(hotel, CHECK_IN, 5, 0),
                        inventory(hotel, CHECK_IN.plusDays(1), 5, 5),
                        inventory(hotel, CHECK_IN.plusDays(2), 5, 0)));

        HotelDetailResponse response = hotelService.getHotel(1L, CHECK_IN, CHECK_OUT);

        assertThat(response.available()).isFalse();
        assertThat(response.roomsLeft()).isZero();
    }

    @Test
    void treatsCheckInOnPreviousCheckOutAsASeparateUnoccupiedNight() {
        LocalDate previousCheckOut = CHECK_IN;
        LocalDate nextCheckOut = CHECK_IN.plusDays(1);
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomInventoryRepository.findForHotelsInRange(
                List.of(1L), rangeStart(previousCheckOut), rangeEnd(nextCheckOut)))
                .willReturn(List.of(inventory(hotel, previousCheckOut, 5, 0)));

        HotelDetailResponse response = hotelService.getHotel(1L, previousCheckOut, nextCheckOut);

        assertThat(response.available()).isTrue();
        assertThat(response.roomsLeft()).isEqualTo(5);
        verify(roomInventoryRepository).findForHotelsInRange(
                List.of(1L), rangeStart(previousCheckOut), rangeEnd(nextCheckOut));
    }

    @Test
    void returnsHotelDetailsWithoutDateFieldsWhenDatesAreOmitted() {
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));

        HotelDetailResponse response = hotelService.getHotel(1L, null, null);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nights()).isNull();
        verifyNoInteractions(roomInventoryRepository);
    }

    @Test
    void rejectsWhenOnlyOneDateIsProvided() {
        assertThatThrownBy(() -> hotelService.searchHotels(null, CHECK_IN, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Both check-in and check-out dates are required.");
        assertThatThrownBy(() -> hotelService.getHotel(1L, CHECK_IN, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Both check-in and check-out dates are required.");
    }

    @Test
    void listSearchUsesOneRangeQueryForAllMatchingHotels() {
        Hotel secondHotel = hotel(2L, "Marine Crest", "Mumbai", "12500.00", 5);
        given(hotelRepository.findByLocationContainingIgnoreCaseOrderByNameAsc("a"))
                .willReturn(List.of(hotel, secondHotel));
        given(roomInventoryRepository.findForHotelsInRange(
                List.of(1L, 2L), rangeStart(CHECK_IN), rangeEnd(CHECK_OUT)))
                .willReturn(List.of(
                        inventory(hotel, CHECK_IN, 5, 0),
                        inventory(hotel, CHECK_IN.plusDays(1), 5, 0),
                        inventory(hotel, CHECK_IN.plusDays(2), 5, 0),
                        inventory(secondHotel, CHECK_IN, 5, 0),
                        inventory(secondHotel, CHECK_IN.plusDays(1), 5, 0),
                        inventory(secondHotel, CHECK_IN.plusDays(2), 5, 0)));

        List<?> responses = hotelService.searchHotels(" a ", CHECK_IN, CHECK_OUT);

        assertThat(responses).hasSize(2);
        assertThat(responses).allMatch(HotelAvailabilityResponse.class::isInstance);
        verify(roomInventoryRepository).findForHotelsInRange(
                List.of(1L, 2L), rangeStart(CHECK_IN), rangeEnd(CHECK_OUT));
    }

    @Test
    void soldOutDatesSuggestThreeNearestAlternativesWithEarlierDateWinningTies() {
        LocalDate requestedCheckIn = TODAY.plusDays(7);
        LocalDate requestedCheckOut = requestedCheckIn.plusDays(2);
        List<RoomInventory> inventory = inventoryRange(
                requestedCheckIn, requestedCheckOut, requestedCheckIn, 2, List.of(), List.of());
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomInventoryRepository.findForHotelsInRange(
                List.of(1L), rangeStart(requestedCheckIn), rangeEnd(requestedCheckOut)))
                .willReturn(inventory);

        HotelDetailResponse response = hotelService.getHotel(1L, requestedCheckIn, requestedCheckOut);

        assertThat(response.available()).isFalse();
        assertThat(response.alternativeDates()).hasSize(3)
                .extracting(alternative -> alternative.checkIn())
                .containsExactly(requestedCheckIn.minusDays(2), requestedCheckIn.plusDays(2),
                        requestedCheckIn.minusDays(3));
        assertThat(response.alternativeDates()).allSatisfy(alternative -> {
            assertThat(alternative.checkOut()).isEqualTo(alternative.checkIn().plusDays(2));
            assertThat(alternative.nights()).isEqualTo(2);
            assertThat(alternative.roomsLeft()).isEqualTo(5);
            assertThat(alternative.totalAmount()).isEqualByComparingTo("16800.00");
        });
        verify(roomInventoryRepository).findForHotelsInRange(
                List.of(1L), rangeStart(requestedCheckIn), rangeEnd(requestedCheckOut));
    }

    @Test
    void excludesPastMissingAndFullCandidateNightsAndReturnsEmptyWhenNoneRemain() {
        LocalDate requestedCheckIn = TODAY.plusDays(1);
        LocalDate requestedCheckOut = requestedCheckIn.plusDays(2);
        LocalDate rangeStart = rangeStart(requestedCheckIn);
        LocalDate rangeEnd = rangeEnd(requestedCheckOut);
        List<RoomInventory> rows = new java.util.ArrayList<>();
        for (LocalDate date = rangeStart; date.isBefore(rangeEnd); date = date.plusDays(1)) {
            if (date.equals(requestedCheckIn.plusDays(4))) {
                continue;
            }
            int booked = date.isBefore(TODAY) || date.equals(TODAY) ? 0 : 5;
            rows.add(inventory(hotel, date, 5, booked));
        }
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomInventoryRepository.findForHotelsInRange(List.of(1L), rangeStart, rangeEnd))
                .willReturn(rows);

        HotelDetailResponse response = hotelService.getHotel(1L, requestedCheckIn, requestedCheckOut);

        assertThat(response.alternativeDates()).isEmpty();
        verify(roomInventoryRepository).findForHotelsInRange(List.of(1L), rangeStart, rangeEnd);
    }

    private List<RoomInventory> inventoryRange(
            LocalDate requestedCheckIn,
            LocalDate requestedCheckOut,
            LocalDate fullStayCheckIn,
            int fullStayNights,
            List<LocalDate> missingDates,
            List<LocalDate> fullDates) {
        List<RoomInventory> rows = new java.util.ArrayList<>();
        LocalDate rangeStart = rangeStart(requestedCheckIn);
        LocalDate rangeEnd = rangeEnd(requestedCheckOut);
        for (LocalDate date = rangeStart; date.isBefore(rangeEnd); date = date.plusDays(1)) {
            if (missingDates.contains(date)) {
                continue;
            }
            boolean requestedNight = !date.isBefore(fullStayCheckIn)
                    && date.isBefore(fullStayCheckIn.plusDays(fullStayNights));
            int booked = requestedNight || fullDates.contains(date) ? 5 : 0;
            rows.add(inventory(hotel, date, 5, booked));
        }
        return rows;
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
    }

    private static LocalDate rangeStart(LocalDate checkIn) {
        return checkIn.minusDays(7);
    }

    private static LocalDate rangeEnd(LocalDate checkOut) {
        return checkOut.plusDays(7);
    }

    private static Hotel hotel(Long id, String name, String location, String nightlyPrice, int totalRooms) {
        Hotel hotel = new Hotel();
        ReflectionTestUtils.setField(hotel, "id", id);
        ReflectionTestUtils.setField(hotel, "name", name);
        ReflectionTestUtils.setField(hotel, "location", location);
        ReflectionTestUtils.setField(hotel, "description", "A comfortable fictional hotel.");
        ReflectionTestUtils.setField(hotel, "amenities", "Wi-Fi, Breakfast");
        ReflectionTestUtils.setField(hotel, "pricePerNight", new BigDecimal(nightlyPrice));
        ReflectionTestUtils.setField(hotel, "totalRooms", totalRooms);
        return hotel;
    }

    private static List<RoomInventory> availableInventory(
            LocalDate checkIn, int count, int totalRooms, int bookedRooms) {
        Hotel hotel = hotel(1L, "Test Hotel", "Test City", "8400.00", totalRooms);
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(offset -> inventory(hotel, checkIn.plusDays(offset), totalRooms, bookedRooms))
                .toList();
    }

    private static RoomInventory inventory(Hotel hotel, LocalDate date, int totalRooms, int bookedRooms) {
        RoomInventory inventory = new RoomInventory();
        ReflectionTestUtils.setField(inventory, "hotel", hotel);
        ReflectionTestUtils.setField(inventory, "stayDate", date);
        ReflectionTestUtils.setField(inventory, "totalRooms", totalRooms);
        ReflectionTestUtils.setField(inventory, "bookedRooms", bookedRooms);
        return inventory;
    }
}
