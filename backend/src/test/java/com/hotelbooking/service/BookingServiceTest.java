package com.hotelbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hotelbooking.dto.BookingRequest;
import com.hotelbooking.dto.BookingResponse;
import com.hotelbooking.entity.Booking;
import com.hotelbooking.entity.BookingStatus;
import com.hotelbooking.entity.Hotel;
import com.hotelbooking.entity.RoomInventory;
import com.hotelbooking.exception.BadRequestException;
import com.hotelbooking.exception.ConflictException;
import com.hotelbooking.repository.BookingRepository;
import com.hotelbooking.repository.HotelRepository;
import com.hotelbooking.repository.RoomInventoryRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final LocalDate CHECK_IN = TODAY.plusDays(2);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(3);

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomInventoryRepository roomInventoryRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SecureRandom secureRandom;

    private Clock clock;
    private BookingService bookingService;
    private Hotel hotel;
    private List<RoomInventory> inventory;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
        bookingService = new BookingService(
                hotelRepository,
                roomInventoryRepository,
                bookingRepository,
                new StayDateValidator(clock),
                clock,
                secureRandom);
        hotel = hotel(1L, "Coconut Grove House", "8400.00");
        inventory = List.of(
                inventory(hotel, CHECK_IN, 5, 0),
                inventory(hotel, CHECK_IN.plusDays(1), 5, 2),
                inventory(hotel, CHECK_IN.plusDays(2), 5, 1));
    }

    @Test
    void booksEveryNightAndReturnsNormalizedConfirmation() {
        mockAvailableBooking();
        given(bookingRepository.save(any(Booking.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(request());

        assertThat(response.reference()).matches("^HB-[A-HJ-NP-Z2-9]{6}$");
        assertThat(response.hotelId()).isEqualTo(1L);
        assertThat(response.hotelName()).isEqualTo("Coconut Grove House");
        assertThat(response.guestName()).isEqualTo("Grace Hopper");
        assertThat(response.guestEmail()).isEqualTo("grace@example.com");
        assertThat(response.checkIn()).isEqualTo(CHECK_IN);
        assertThat(response.checkOut()).isEqualTo(CHECK_OUT);
        assertThat(response.nights()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("25200.00");
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(inventory).extracting(RoomInventory::getBookedRooms).containsExactly(1, 3, 2);
        verify(roomInventoryRepository).findForStayForUpdate(1L, CHECK_IN, CHECK_OUT);
        verify(roomInventoryRepository, never()).findForStay(any(), any(), any());
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void rejectsIfAnyNightIsSoldOutAndDoesNotSave() {
        inventory.get(1).reserveOneRoom();
        inventory.get(1).reserveOneRoom();
        inventory.get(1).reserveOneRoom();
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomInventoryRepository.findForStayForUpdate(1L, CHECK_IN, CHECK_OUT))
                .willReturn(inventory);

        assertThatThrownBy(() -> bookingService.createBooking(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No rooms available for the selected dates");

        assertThat(inventory).extracting(RoomInventory::getBookedRooms).containsExactly(0, 5, 1);
        verify(bookingRepository, never()).save(any(Booking.class));
        verifyNoInteractions(secureRandom);
    }

    @Test
    void rejectsWhenInventoryDoesNotContainEveryNight() {
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomInventoryRepository.findForStayForUpdate(1L, CHECK_IN, CHECK_OUT))
                .willReturn(inventory.subList(0, 2));

        assertThatThrownBy(() -> bookingService.createBooking(request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Selected dates are outside the bookable window.");

        verify(bookingRepository, never()).save(any(Booking.class));
        verifyNoInteractions(secureRandom);
    }

    @Test
    void retriesReferenceWhenCandidateAlreadyExists() {
        mockAvailableBooking();
        given(bookingRepository.existsByReference("HB-ABCDEF")).willReturn(true);
        given(bookingRepository.existsByReference("HB-ABCDEG")).willReturn(false);
        given(bookingRepository.save(any(Booking.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        int[] nextIndices = {0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 6};
        AtomicInteger index = new AtomicInteger();
        given(secureRandom.nextInt(anyInt()))
                .willAnswer(invocation -> nextIndices[index.getAndIncrement()]);

        BookingResponse response = bookingService.createBooking(request());

        assertThat(response.reference()).isEqualTo("HB-ABCDEG");
        verify(bookingRepository).existsByReference("HB-ABCDEF");
        verify(bookingRepository).existsByReference("HB-ABCDEG");
    }

    private void mockAvailableBooking() {
        given(hotelRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(roomInventoryRepository.findForStayForUpdate(1L, CHECK_IN, CHECK_OUT))
                .willReturn(inventory);
    }

    private static BookingRequest request() {
        return new BookingRequest(1L, CHECK_IN, CHECK_OUT,
                "  Grace Hopper  ", "  GRACE@EXAMPLE.COM ");
    }

    private static Hotel hotel(Long id, String name, String nightlyPrice) {
        Hotel hotel = new Hotel();
        ReflectionTestUtils.setField(hotel, "id", id);
        ReflectionTestUtils.setField(hotel, "name", name);
        ReflectionTestUtils.setField(hotel, "location", "Goa");
        ReflectionTestUtils.setField(hotel, "description", "A comfortable fictional hotel.");
        ReflectionTestUtils.setField(hotel, "amenities", "Wi-Fi, Breakfast");
        ReflectionTestUtils.setField(hotel, "pricePerNight", new BigDecimal(nightlyPrice));
        ReflectionTestUtils.setField(hotel, "totalRooms", 5);
        return hotel;
    }

    private static RoomInventory inventory(Hotel hotel, LocalDate date, int totalRooms, int bookedRooms) {
        RoomInventory room = new RoomInventory();
        ReflectionTestUtils.setField(room, "hotel", hotel);
        ReflectionTestUtils.setField(room, "stayDate", date);
        ReflectionTestUtils.setField(room, "totalRooms", totalRooms);
        ReflectionTestUtils.setField(room, "bookedRooms", bookedRooms);
        return room;
    }
}
