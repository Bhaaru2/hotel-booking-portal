package com.hotelbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hotelbooking.entity.Booking;
import com.hotelbooking.entity.BookingStatus;
import com.hotelbooking.entity.Hotel;
import com.hotelbooking.entity.RoomInventory;
import com.hotelbooking.exception.BadRequestException;
import com.hotelbooking.exception.ResourceNotFoundException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookingLookupCancelServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final LocalDate CHECK_IN = TODAY.plusDays(2);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(3);
    private static final String MISSING_MESSAGE = "Booking not found for the given reference and email";

    @Mock private HotelRepository hotelRepository;
    @Mock private RoomInventoryRepository inventoryRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private SecureRandom secureRandom;

    private BookingService service;
    private Booking booking;
    private List<RoomInventory> inventory;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
        service = new BookingService(hotelRepository, inventoryRepository, bookingRepository,
                new StayDateValidator(clock), clock, secureRandom);
        Hotel hotel = new Hotel();
        ReflectionTestUtils.setField(hotel, "id", 77L);
        ReflectionTestUtils.setField(hotel, "name", "Test Hotel");
        ReflectionTestUtils.setField(hotel, "pricePerNight", new BigDecimal("100.00"));
        booking = new Booking("HB-7K2M9Q", hotel, "Guest Name", "guest@example.com", CHECK_IN,
                CHECK_OUT, 3, new BigDecimal("300.00"), BookingStatus.CONFIRMED,
                LocalDateTime.of(2026, 11, 13, 0, 0));
        inventory = List.of(inventory(hotel, CHECK_IN, 0), inventory(hotel, CHECK_IN.plusDays(1), 0),
                inventory(hotel, CHECK_IN.plusDays(2), 0));
    }

    @Test
    void lookupNormalizesReferenceAndEmailAndReturnsBooking() {
        given(bookingRepository.findByReferenceAndGuestEmail("HB-7K2M9Q", "guest@example.com"))
                .willReturn(Optional.of(booking));
        assertThat(service.findBooking(" hb-7k2m9q ", " GUEST@EXAMPLE.COM ").reference()).isEqualTo("HB-7K2M9Q");
    }

    @Test
    void wrongEmailAndUnknownReferenceUseSameNotFoundMessage() {
        given(bookingRepository.findByReferenceAndGuestEmail(any(), any())).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.findBooking("HB-7K2M9Q", "wrong@example.com"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage(MISSING_MESSAGE);
        assertThatThrownBy(() -> service.findBooking("HB-UNKNOWN", "guest@example.com"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage(MISSING_MESSAGE);
    }

    @Test
    void cancellationMarksBookingAndReleasesEachNightUsingLockingQuery() {
        given(bookingRepository.findForCancellation("HB-7K2M9Q", "guest@example.com"))
                .willReturn(Optional.of(booking));
        given(inventoryRepository.findForStayForUpdate(77L, CHECK_IN, CHECK_OUT)).willReturn(inventory);
        given(bookingRepository.save(booking)).willReturn(booking);
        inventory.forEach(RoomInventory::reserveOneRoom);
        var response = service.cancelBooking(" hb-7k2m9q ", " GUEST@EXAMPLE.COM ");
        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.alreadyCancelled()).isNull();
        assertThat(inventory).extracting(RoomInventory::getBookedRooms).containsExactly(0, 0, 0);
        verify(inventoryRepository).findForStayForUpdate(77L, CHECK_IN, CHECK_OUT);
        verify(bookingRepository).save(booking);
    }

    @Test
    void repeatedCancellationDoesNotLockOrChangeInventory() {
        booking.markCancelled();
        given(bookingRepository.findForCancellation("HB-7K2M9Q", "guest@example.com"))
                .willReturn(Optional.of(booking));
        var response = service.cancelBooking("HB-7K2M9Q", "guest@example.com");
        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.alreadyCancelled()).isTrue();
        verify(inventoryRepository, never()).findForStayForUpdate(any(), any(), any());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void wrongEmailAndPastBookingDoNotChangeInventory() {
        given(bookingRepository.findForCancellation("HB-7K2M9Q", "wrong@example.com"))
                .willReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancelBooking("HB-7K2M9Q", "wrong@example.com"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage(MISSING_MESSAGE);
        verify(inventoryRepository, never()).findForStayForUpdate(any(), any(), any());
        Booking past = new Booking("HB-7K2M9Q", booking.getHotel(), "Guest Name", "guest@example.com",
                TODAY.minusDays(1), TODAY.plusDays(2), 3, new BigDecimal("300.00"), BookingStatus.CONFIRMED,
                booking.getCreatedAt());
        given(bookingRepository.findForCancellation("HB-7K2M9Q", "guest@example.com"))
                .willReturn(Optional.of(past));
        assertThatThrownBy(() -> service.cancelBooking("HB-7K2M9Q", "guest@example.com"))
                .isInstanceOf(BadRequestException.class).hasMessage("Past bookings cannot be cancelled");
        verify(inventoryRepository, never()).findForStayForUpdate(any(), any(), any());
    }

    private static RoomInventory inventory(Hotel hotel, LocalDate date, int booked) {
        RoomInventory row = new RoomInventory();
        ReflectionTestUtils.setField(row, "hotel", hotel);
        ReflectionTestUtils.setField(row, "stayDate", date);
        ReflectionTestUtils.setField(row, "totalRooms", 3);
        ReflectionTestUtils.setField(row, "bookedRooms", booked);
        return row;
    }
}
