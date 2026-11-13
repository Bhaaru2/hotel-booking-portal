# Design

## Architecture overview

The frontend is a React single-page application. It calls the Spring Boot API, which applies validation and booking rules and uses Spring Data JPA to read and write MySQL data. Flyway owns schema creation and sample data.

```text
Browser
  └── React + TypeScript (Vite)
        └── HTTP / JSON: /api/*
              └── Spring Boot controllers
                    └── services
                          └── Spring Data repositories / JPA
                                └── MySQL 8+
```

The backend follows `controller -> service -> repository`. Controllers return DTOs; they do not return JPA entities. `GlobalExceptionHandler` formats API errors.

## Database design

Flyway migrations are in `backend/src/main/resources/db/migration`. All tables use InnoDB and `utf8mb4`.

### `hotels`

- `id`: generated `BIGINT` primary key.
- `name`, `location`: required `VARCHAR(150)` values.
- `description`, `amenities`: required `TEXT`; amenities are stored as comma-separated text.
- `price_per_night`: required `DECIMAL(10,2)`.
- `total_rooms`: required integer room count.
- Index: `idx_hotels_location` on `location` for location searches.

The seed migration inserts six hotels in Mumbai, Goa, Jaipur, Delhi, Bengaluru and Kochi. Their names and descriptions are fictional sample data.

### `room_inventory`

- `id`: generated `BIGINT` primary key.
- `hotel_id`: required foreign key to `hotels.id`.
- `stay_date`: required `DATE`.
- `total_rooms`: room capacity for this hotel on this night.
- `booked_rooms`: required integer, default 0.
- Unique constraint: `uq_room_inventory_hotel_stay_date` on `(hotel_id, stay_date)`.
- Check constraint: `chk_room_inventory_booked_rooms` requires `booked_rooms` to be between 0 and `total_rooms`.
- The unique constraint also supplies an index for the hotel/date lookup pattern.

Inventory is stored once per hotel per night so overlapping stays can use and change the exact nights they occupy. The seed migration creates rows for 120 consecutive dates beginning with `CURDATE()` when the migration runs.

### `bookings`

- `id`: generated `BIGINT` primary key.
- `reference`: required `VARCHAR(9)`, unique constraint `uq_bookings_reference`.
- `hotel_id`: required foreign key to `hotels.id`.
- `guest_name`: required `VARCHAR(150)`; the request validator limits it to 100 characters.
- `guest_email`: required `VARCHAR(254)`; the request validator limits it to 150 characters.
- `check_in`, `check_out`: required `DATE` values.
- `nights`: required integer.
- `total_amount`: required `DECIMAL(10,2)`.
- `status`: MySQL enum with `CONFIRMED` and `CANCELLED` values.
- `created_at`: required timestamp, defaulting to the database's current timestamp when omitted.
- Index: `idx_bookings_reference_guest_email` on `(reference, guest_email)`.

## API design

The API uses JSON and lives under `/api`. Dates use ISO `YYYY-MM-DD` values. Hotel responses are DTOs and contain amenities as an array. Quote totals come from the backend.

Errors use one JSON shape:

```json
{
  "timestamp": "ISO-8601 timestamp",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed.",
  "path": "/api/bookings",
  "fieldErrors": [{ "field": "guestName", "message": "Guest name is required." }]
}
```

Validation errors and invalid parameters return 400, missing resources return 404, and booking conflicts return 409. Unknown paths also return 404 in this format. Unexpected exceptions are logged on the server and return a generic 500 message.

| Method and path | Purpose | Typical status codes |
|---|---|---|
| `GET /api/hotels?location=&checkIn=&checkOut=` | List/search hotels; date parameters add availability, price quote and alternatives. | 200, 400 |
| `GET /api/hotels/{id}?checkIn=&checkOut=` | Hotel details with optional availability and quote. | 200, 400, 404 |
| `POST /api/bookings` | Create one booking. | 201, 400, 404, 409 |
| `GET /api/bookings/{reference}?email=` | Find a booking using both reference and email. | 200, 400, 404 |
| `POST /api/bookings/{reference}/cancel` | Cancel a booking using the email in the request body. | 200, 400, 404, 500 |

There is no endpoint for listing all bookings.

## Booking logic

`ClockConfiguration` provides a clock in `Asia/Kolkata`. `StayDateValidator` requires both dates, requires check-out to be after check-in, allows check-in today but not in the past, and caps stays at 30 nights. The services also require an inventory row for every occupied night; a missing row means the requested date is outside the bookable window.

A stay from check-in to check-out occupies every night starting at check-in and ending the day before check-out. The check-out date itself is free. The backend calculates the total as `nights × price_per_night` using `BigDecimal`.

Availability uses one range query for the requested hotel or hotels. A hotel is available only when every occupied night has a free room. `roomsLeft` is the smallest remaining capacity among those nights. The frontend displays the returned price and does not calculate a booking total.

Booking references use `SecureRandom`, the `HB-` prefix and six characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`. The service checks for an existing reference and retries up to five times; the database unique constraint is the final uniqueness guard. Guest names are trimmed and email addresses are trimmed and lowercased before storage.

Lookup trims and uppercases the reference, and trims and lowercases the email. The query matches both values. An unknown reference and an email mismatch produce the same 404 message: `Booking not found for the given reference and email`.

Cancellation first locks the booking record for that reference/email pair. A cancelled booking returns with `alreadyCancelled: true` and inventory is not touched again. A confirmed booking with check-in before today cannot be cancelled. Otherwise, the service locks its inventory rows, in date order, decrements each row and marks the booking `CANCELLED`. Missing or inconsistent inventory during cancellation raises an internal error rather than silently hiding the problem.

## Overlapping stays

Capacity is counted on each night in `room_inventory`. For example, a booking from 10 June to 12 June occupies the nights of 10 and 11 June. A second booking from 11 June to 13 June overlaps on the night of 11 June, so both bookings consume a room on that night. A booking from 12 June to 13 June starts on the first booking's check-out date and occupies only the 12 June night, so those two stays do not overlap.

Each booking is allowed only if all its occupied nights have capacity. This prevents a reservation from being confirmed when just one night is sold out.

## Simultaneous bookings

The race is two guests reading the last available room at nearly the same time and both attempting to book it. `BookingService.createBooking` locks the stay's inventory rows with a pessimistic write lock before checking capacity or incrementing counts. The repository query orders rows by `stay_date`, so transactions that overlap dates acquire locks in the same order. A waiting transaction checks the updated room count after the first transaction commits, and then receives a conflict if no rooms remain.

The trade-off is that locks are held until the transaction completes. They apply to the inventory rows for the selected hotel's occupied dates; unrelated hotel/date rows are not deliberately locked. An alternative is optimistic locking with a version column and retries after conflicts, but this implementation does not use that approach.

`BookingConcurrencyTest` is a MySQL-backed Spring Boot integration test gated on `DB_URL`. It creates and cleans up its own `ZZ-TEST` hotel and inventory. Its three cases try to book the final three rooms from ten threads, book overlapping date ranges and check that the calls finish and nightly counts match, and cancel/rebook while two cancellation requests race. The database-free test run skips these cases; they have not been run against MySQL as part of this documentation update.

## Surprise feature

When a requested stay is sold out, the backend checks check-in dates up to seven days before and after the requested check-in, excluding the requested date. It keeps the same stay length, excludes past dates, and requires every candidate night to exist in inventory and have capacity. One read-only inventory-range query is evaluated in memory; candidates are sorted by distance, with earlier dates first for equal distances, and limited to three. Each suggestion includes its dates, nights, rooms left and backend-calculated total. The details page and search cards make these dates clickable. If there is no available candidate, the details page says so. The search window is limited to seven days each way, the list to three results, and the stay length cannot change.

## Frontend design

The React app uses `BrowserRouter` and has these routes:

- `/`: hotel search and results. Location and dates are kept in the query string.
- `/hotels/:id`: details, date selection, quote, availability and alternative suggestions.
- `/hotels/:id/book`: booking form. It loads the quote and availability before showing the form.
- `/booking/confirmed`: confirmation from router state; a refresh does not restore the booking details.
- `/find-booking`: lookup and cancellation using reference and email.
- Any other path shows the not-found page.

API calls are in `frontend/src/api/client.ts`. The base URL uses `VITE_API_BASE_URL` when set and defaults to `/api`; the Vite development server proxies `/api` to `http://localhost:8080`. Shared date validation prevents partial date ranges, check-out on/before check-in and past check-in dates, using the browser's local date. Booking form field errors are shown inline. API error messages are shown to the user; booking lookup maps all 404 responses to one generic message. The booking email is kept in the lookup page's component state, not in the URL or persistent browser storage.

## Testing approach

Backend service tests use JUnit 5 and Mockito, including a fixed `Clock` for date rules, price calculation, availability, reference generation, cancellation and alternative-date selection. MVC tests use `@WebMvcTest` and `MockMvc` to check response shapes, validation and error handling. The MySQL-backed concurrency integration tests run only when `DB_URL` is set. Frontend `npm run build` runs the TypeScript project build and Vite production build; there are no automated frontend tests in the current `package.json` scripts.

In the verification run for this version, `mvn clean verify` reported 37 tests, 0 failures, 0 errors and 3 skipped MySQL integration tests. `npm run build` passed.

## Design decisions and trade-offs

- Inventory is pre-seeded for 120 consecutive days from the migration date. This makes availability queries straightforward but prevents booking outside that window.
- A booking reserves one room. There is one room category per hotel; room types and guest counts are not modeled.
- `LocalDate` is used for stay dates and `BigDecimal`/`DECIMAL(10,2)` for money.
- The backend calculates all quote and booking totals. The frontend only formats returned amounts.
- Reference plus email is required for lookup and cancellation. Returning the same 404 on a mismatch avoids confirming whether a reference exists, but this is not a substitute for full authentication.
- Pessimistic row locking is simple for this small MySQL app and protects the last room. The cost is waiting when another transaction needs the same hotel/date rows.
- There is no authentication. Booking data is returned to a caller who supplies the matching reference and email.

## Assumptions

- Inventory is pre-seeded for 120 days from the migration date; dates outside that window are not bookable.
- A stay occupies check-in through the night before check-out; the check-out day is available for a back-to-back stay.
- There is one room category per hotel, and each booking reserves exactly one room.
- A stay can be at most 30 nights.
- “Today” for booking date rules is determined using the `Asia/Kolkata` time zone. Check-in today is allowed.
- A booking with check-in before today cannot be cancelled; a booking checking in today remains cancellable.
- Booking lookup and cancellation require both the reference and the email used at booking time.

## Limitations and what I would improve with more time

- Add authentication or another stronger owner-verification flow, since email plus reference is the only booking access check.
- Add room types, multi-room/guest counts, pagination and payments only if the product requires them.
- Replace the fixed 120-day seed window with inventory management for later dates.
- Run the gated concurrency and cancellation integration tests against a fresh local MySQL database and complete a fresh-clone setup test.
- Add a small frontend test suite.
- Add deployment configuration and operational logging/monitoring if the app were to be hosted.
