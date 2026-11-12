# AGENTS.md

Hotel Booking Portal: a take-home interview assignment (Thomas Cook). Deadline is tight, so keep scope small and finish everything end to end. A small, working, well-explained app beats a big, half-done one.

## Stack (do not change)

- Backend: Java 17 (or 21), Spring Boot 3, Maven, Spring Data JPA, Bean Validation
- Frontend: React + Vite (JavaScript or TypeScript), plain CSS or Tailwind, fetch/axios
- Database: MySQL 8, schema managed with **Flyway** migrations
- Tests: JUnit 5 + Mockito (H2 or Testcontainers only if quick; otherwise unit-test the service layer)

## Repo layout

```
/backend      Spring Boot app
/frontend     React app
/docs         screenshots, demo recording
README.md     prerequisites + step-by-step local run + time spent + AI disclosure
DESIGN.md     design write-up (see list below)
```

## Hard rules

1. **No secrets in the repo.** DB credentials come from environment variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`). Commit `.env.example` and `application.properties` with `${ENV_VAR}` placeholders only. Never commit `.env`.
2. **Backend calculates the total price** (nights x price per night). The frontend only displays what the API returns. Never trust a price from the client.
3. Layering: `controller -> service -> repository`. Controllers use DTOs, never expose JPA entities. No business logic in controllers.
4. **Consistent error format** for every failure via a global `@RestControllerAdvice`:
   `{ "timestamp", "status", "error", "message", "path", "fieldErrors": [] }`
   Use correct HTTP codes (400 validation, 404 not found, 409 no availability / conflict).
5. Validate on the backend with Bean Validation plus service-level checks. Show clear messages in the UI.
6. Commit in small steps with conventional messages (`feat:`, `fix:`, `test:`, `docs:`, `chore:`). Run build and tests before each commit.
7. Keep code simple and readable. Short comments where the logic is not obvious (especially booking and locking). I must be able to explain every file in an interview.

## Functional requirements

User can:
- Browse hotels: name, location, description, amenities, price per night
- Search by location + check-in/check-out dates
- View hotel details and available rooms for the selected dates
- Book one room with name + email
- See the total amount before confirming
- Get a confirmation with a **unique booking reference**
- Retrieve a booking by **reference + email**, and cancel it

Seed at least **5 hotels** (one room category per hotel is enough).

## Booking rules

- check-out must be after check-in; check-in cannot be in the past
- Total = nights x price per night, computed on the backend
- Availability is tracked **per night**. Use a `room_inventory` table (hotel_id, date, total_rooms, booked_rooms) with a unique key on (hotel_id, date). A stay occupies nights `check-in` through `check-out - 1` (check-out day is NOT occupied, so back-to-back stays do not conflict).
- Booking fails with 409 if any night in the stay has no free room
- Cancelling a booking releases the inventory for each night
- **Concurrency:** inside one `@Transactional` method, lock the affected inventory rows with `SELECT ... FOR UPDATE` (pessimistic write lock, ordered by date to avoid deadlocks), check capacity, then increment `booked_rooms` and insert the booking. This is how two users booking the last room are handled.
- Booking reference: short, unique, human-friendly (e.g. `HB-7K2M9Q`), with a unique DB constraint
- Cancellation is idempotent-safe: cancelling an already cancelled booking returns a clear message, and does not release inventory twice

## Design decisions

- Seed `room_inventory` for 120 days from the migration date; dates outside that range cannot be booked.
- A stay occupies each night from check-in through the night before check-out. The check-out day is free, allowing back-to-back stays.
- Generate short, random, human-friendly booking references (for example, `HB-7K2M9Q`) and enforce uniqueness in the database.
- Use `LocalDate` for dates and `BigDecimal` / `DECIMAL(10,2)` for money; never use `double` for prices.
- Booking lookup and cancellation require both reference and email. Return 404 for a wrong email, as for an unknown reference, so booking references cannot be probed.

## API (suggested)

- `GET  /api/hotels?location=&checkIn=&checkOut=` list/search with availability
- `GET  /api/hotels/{id}?checkIn=&checkOut=` details + availability + price quote
- `POST /api/bookings` create booking (hotelId, checkIn, checkOut, guestName, guestEmail)
- `POST /api/bookings/lookup` or `GET /api/bookings/{reference}?email=` retrieve
- `POST /api/bookings/{reference}/cancel` cancel (requires email)

## Surprise feature

ONE small, finished feature. Chosen feature: Chosen feature: **Alternative dates when a search has no availability.** If a hotel has no free room for the selected dates, the API also returns the nearest check-in dates (same number of nights, within +/- 7 days) that do have availability, and the UI shows them as clickable suggestions. Reuse the same per-night availability query. Keep it small: one service method, one extra field in the search/detail response, one UI component.

## Required automated tests (minimum)

- Total price calculation (nights x price)
- Date validation (check-out after check-in, check-in not in the past)
- Availability: overlapping stays reduce capacity; booking beyond capacity is rejected
- Back-to-back stays (check-out day = next check-in day) are allowed
- Cancellation releases availability

## DESIGN.md must cover

Architecture, database design (entities, relationships, constraints), API design, booking logic (pricing, date validation, availability, cancellation), design decisions and trade-offs, simultaneous bookings (the last-room problem), surprise feature, limitations and improvements.

## README.md must include

Prerequisites, DB setup + migrations + sample data, how to run backend and frontend, how to run tests, screenshots or demo recording, **approximate time spent**, unfinished items, and a **disclosure that AI tools (Codex) were used significantly**.

## Not required (do not build)

Authentication, payments, admin panel, cloud deployment.

## Working style

- Plan briefly, then implement one feature at a time, vertical slice by slice: DB migration -> entity -> repository -> service -> controller -> test -> UI.
- After each slice: build passes, tests pass, then commit.
- If something is ambiguous, pick the simplest reasonable assumption and list it in DESIGN.md under assumptions.
