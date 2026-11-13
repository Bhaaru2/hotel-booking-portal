package com.hotelbooking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotelbooking.dto.BookingRequest;
import com.hotelbooking.dto.BookingResponse;
import com.hotelbooking.exception.ConflictException;
import com.hotelbooking.entity.BookingStatus;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class BookingConcurrencyTest {

    private static final int TEST_ROOM_COUNT = 3;
    private static final int INVENTORY_DAYS = 5;
    private static final int TIMEOUT_SECONDS = 20;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private Clock clock;

    private Long testHotelId;
    private LocalDate firstInventoryDate;
    private ExecutorService executor;

    @BeforeEach
    void createIsolatedHotelAndInventory() {
        firstInventoryDate = LocalDate.now(clock).plusDays(2);
        executor = Executors.newFixedThreadPool(10);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update((Connection connection) -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO hotels "
                                + "(name, location, description, amenities, price_per_night, total_rooms) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, "ZZ-TEST-" + UUID.randomUUID());
                statement.setString(2, "Concurrency Test City");
                statement.setString(3, "Dedicated hotel row for booking concurrency tests.");
                statement.setString(4, "Wi-Fi, Test parking");
                statement.setBigDecimal(5, new BigDecimal("100.00"));
                statement.setInt(6, TEST_ROOM_COUNT);
                return statement;
            }, keyHolder);
            testHotelId = keyHolder.getKey().longValue();

            for (int day = 0; day < INVENTORY_DAYS; day++) {
                jdbcTemplate.update(
                        "INSERT INTO room_inventory (hotel_id, stay_date, total_rooms, booked_rooms) "
                                + "VALUES (?, ?, ?, 0)",
                        testHotelId,
                        firstInventoryDate.plusDays(day),
                        TEST_ROOM_COUNT);
            }
        } catch (RuntimeException failure) {
            cleanupTestData();
            throw failure;
        }
    }

    @AfterEach
    void cleanUpTestData() {
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        cleanupTestData();
    }

    @Test
    void simultaneousGuestsCannotBookMoreThanTheLastThreeRooms() throws Exception {
        // This race verifies that guests contending for the same final rooms serialize on the inventory rows.
        LocalDate checkIn = firstInventoryDate;
        LocalDate checkOut = checkIn.plusDays(3);
        List<BookingRequest> requests = requests(10, checkIn, checkOut, "race");
        List<Outcome> outcomes = runTogether(requests);

        assertThat(outcomes.stream().filter(Outcome::succeeded).count()).isEqualTo(3);
        assertThat(outcomes.stream().filter(Outcome::conflicted).count()).isEqualTo(7);
        assertThat(outcomes).allMatch(outcome -> outcome.succeeded() || outcome.conflicted());

        assertThat(bookedRoomsByDate())
                .containsEntry(firstInventoryDate, 3)
                .containsEntry(firstInventoryDate.plusDays(1), 3)
                .containsEntry(firstInventoryDate.plusDays(2), 3)
                .containsEntry(firstInventoryDate.plusDays(3), 0)
                .containsEntry(firstInventoryDate.plusDays(4), 0);

        assertThat(countBookingsForTestHotel()).isEqualTo(3);
        assertThat(countUniqueReferencesForTestHotel()).isEqualTo(3);
    }

    @Test
    void overlappingRangesCompleteWithoutDeadlockAndMatchNightlyCounts() throws Exception {
        LocalDate firstRangeCheckIn = firstInventoryDate;
        LocalDate firstRangeCheckOut = firstInventoryDate.plusDays(3);
        LocalDate secondRangeCheckIn = firstInventoryDate.plusDays(1);
        LocalDate secondRangeCheckOut = firstInventoryDate.plusDays(4);
        List<BookingRequest> requests = List.of(
                request(0, firstRangeCheckIn, firstRangeCheckOut, "range-a"),
                request(1, firstRangeCheckIn, firstRangeCheckOut, "range-a"),
                request(2, secondRangeCheckIn, secondRangeCheckOut, "range-b"));

        List<Outcome> outcomes = runTogether(requests);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(bookedRoomsByDate()).containsExactlyInAnyOrderEntriesOf(expectedCounts(requests, outcomes));
    }

    @Test
    void cancellationReleasesRoomsOnceEvenWhenCancelledConcurrently() throws Exception {
        LocalDate checkIn = firstInventoryDate;
        LocalDate checkOut = checkIn.plusDays(3);
        List<BookingResponse> initialBookings = new ArrayList<>();
        for (int guest = 0; guest < TEST_ROOM_COUNT; guest++) {
            initialBookings.add(bookingService.createBooking(request(guest, checkIn, checkOut, "cancel")));
        }

        BookingResponse cancelled = bookingService.cancelBooking(
                initialBookings.get(0).reference(), initialBookings.get(0).guestEmail());
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookedRoomsByDate()).containsEntry(checkIn, 2)
                .containsEntry(checkIn.plusDays(1), 2)
                .containsEntry(checkIn.plusDays(2), 2);

        BookingResponse replacement = bookingService.createBooking(request(4, checkIn, checkOut, "replacement"));
        assertThat(bookedRoomsByDate()).containsEntry(checkIn, 3)
                .containsEntry(checkIn.plusDays(1), 3)
                .containsEntry(checkIn.plusDays(2), 3);

        // Two cancellation requests for one booking must not release the same room twice.
        List<BookingResponse> concurrentCancels = runCancelsTogether(replacement);
        assertThat(concurrentCancels).allMatch(response -> response.status() == BookingStatus.CANCELLED);
        assertThat(concurrentCancels).extracting(BookingResponse::alreadyCancelled)
                .containsExactlyInAnyOrder(null, true);
        assertThat(bookedRoomsByDate()).containsEntry(checkIn, 2)
                .containsEntry(checkIn.plusDays(1), 2)
                .containsEntry(checkIn.plusDays(2), 2)
                .containsEntry(checkIn.plusDays(3), 0)
                .containsEntry(checkIn.plusDays(4), 0);
    }

    private List<BookingResponse> runCancelsTogether(BookingResponse booking) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BookingResponse>> futures = new ArrayList<>();
        for (int thread = 0; thread < 2; thread++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent cancellation start.");
                }
                return bookingService.cancelBooking(booking.reference(), booking.guestEmail());
            }));
        }
        assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        return futures.stream().map(this::getCancelWithinTimeout).toList();
    }

    private BookingResponse getCancelWithinTimeout(Future<BookingResponse> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("Concurrent cancellation did not finish successfully.", failure);
        }
    }

    private List<Outcome> runTogether(List<BookingRequest> requests) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();

        for (BookingRequest request : requests) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for the concurrent start signal.");
                }
                try {
                    return new Outcome(bookingService.createBooking(request), null);
                } catch (ConflictException conflict) {
                    return new Outcome(null, conflict);
                }
            }));
        }

        assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        return futures.stream()
                .map(future -> getWithinTimeout(future))
                .toList();
    }

    private Outcome getWithinTimeout(Future<Outcome> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("Concurrent booking did not finish successfully.", failure);
        }
    }

    private Map<LocalDate, Integer> bookedRoomsByDate() {
        return jdbcTemplate.query(
                "SELECT stay_date, booked_rooms FROM room_inventory "
                        + "WHERE hotel_id = ? ORDER BY stay_date",
                resultSet -> {
                    Map<LocalDate, Integer> counts = new HashMap<>();
                    while (resultSet.next()) {
                        counts.put(resultSet.getDate("stay_date").toLocalDate(),
                                resultSet.getInt("booked_rooms"));
                    }
                    return counts;
                },
                testHotelId);
    }

    private Map<LocalDate, Integer> expectedCounts(
            List<BookingRequest> requests, List<Outcome> outcomes) {
        Map<LocalDate, Integer> expected = new HashMap<>();
        for (int day = 0; day < INVENTORY_DAYS; day++) {
            LocalDate stayDate = firstInventoryDate.plusDays(day);
            int bookingsThisNight = 0;
            for (int index = 0; index < requests.size(); index++) {
                BookingRequest request = requests.get(index);
                boolean occupied = !stayDate.isBefore(request.checkIn())
                        && stayDate.isBefore(request.checkOut());
                if (outcomes.get(index).succeeded() && occupied) {
                    bookingsThisNight++;
                }
            }
            expected.put(stayDate, bookingsThisNight);
        }
        return expected;
    }

    private long countBookingsForTestHotel() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE hotel_id = ?", Long.class, testHotelId);
    }

    private long countUniqueReferencesForTestHotel() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT reference) FROM bookings WHERE hotel_id = ?", Long.class, testHotelId);
    }

    private List<BookingRequest> requests(
            int count, LocalDate checkIn, LocalDate checkOut, String prefix) {
        List<BookingRequest> requests = new ArrayList<>();
        for (int guest = 0; guest < count; guest++) {
            requests.add(request(guest, checkIn, checkOut, prefix));
        }
        return requests;
    }

    private BookingRequest request(int guest, LocalDate checkIn, LocalDate checkOut, String prefix) {
        return new BookingRequest(
                testHotelId,
                checkIn,
                checkOut,
                "Concurrent Guest " + guest,
                prefix + guest + "@example.com");
    }

    private void cleanupTestData() {
        if (testHotelId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM bookings WHERE hotel_id = ?", testHotelId);
        jdbcTemplate.update("DELETE FROM room_inventory WHERE hotel_id = ?", testHotelId);
        jdbcTemplate.update("DELETE FROM hotels WHERE id = ?", testHotelId);
        testHotelId = null;
    }

    private record Outcome(BookingResponse booking, ConflictException conflict) {
        boolean succeeded() {
            return booking != null;
        }

        boolean conflicted() {
            return conflict != null;
        }
    }
}
