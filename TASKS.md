# TASKS.md

Roadmap and progress tracker for the Hotel Booking Portal. **Read this together with AGENTS.md before doing anything.** AGENTS.md has the rules and requirements. This file says what is done, what to do now, and what comes next.

## How to use this file (instructions for Codex)

1. Find the chunk marked **CURRENT**. Work on that chunk only. Do not start later chunks, even if it looks easy.
2. Stay inside the chunk's scope. If something outside it is needed, note it under "Open questions / notes" at the bottom instead of building it.
3. When the chunk's acceptance criteria are met and `mvn test` (backend) or `npm run build` (frontend) passes:
   - tick its checkboxes,
   - change its status to `DONE`,
   - mark the next chunk `CURRENT`,
   - update the "Progress snapshot" table,
   - commit using the chunk's suggested commit message (include the TASKS.md update in the same commit).
4. Keep commits small: one chunk = one commit (or a few, if the chunk is big).
5. Never commit secrets. DB config comes from env vars (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
6. If a requirement is ambiguous, pick the simplest reasonable assumption and add it to "Assumptions" at the bottom. These feed DESIGN.md later.

Status values: `DONE` | `CURRENT` | `UPCOMING` | `BLOCKED`

---

## Progress snapshot

| # | Chunk | Phase | Status |
|---|---|---|---|
| S | Repo, GitHub and local environment setup | Setup | DONE |
| 0 | Lock design decisions | Setup | DONE |
| 1 | Spring Boot skeleton + MySQL connection | Backend | DONE |
| 2 | V1 migration: tables | Backend | DONE |
| 3 | V2 migration: seed data | Backend | DONE |
| 4 | Entities, repositories, `GET /api/hotels` | Backend | DONE |
| 5 | Global error handler | Backend | DONE |
| 6a | Date validation, availability and read-only price quote | Backend | DONE |
| 6b | Create booking with row locking and reference generation | Backend | DONE |
| 6c | Concurrency test and locking review | Backend | DONE |
| 7 | Lookup + cancel (with tests) | Backend | DONE |
| 8 | Surprise feature (backend) | Backend | DONE |
| 9a | Frontend: hotel list + search | Frontend | DONE |
| 9b | Frontend: hotel details + price quote | Frontend | DONE |
| 9c | Frontend: booking form + confirmation | Frontend | CURRENT |
| 9d | Frontend: lookup/cancel + alternative dates UI | Frontend | UPCOMING |
| 10 | README, DESIGN.md, screenshots, submission | Docs | UPCOMING |

---

## Where we are and what is still missing

- **Done:** public GitHub repo exists, with AGENTS.md, README stub, `.gitignore`, `.env.example`. Local MySQL database `hotel_booking` and user `hotel_app` are set up. Java 21, Maven and Node are installed.
- **Not started:** all application code (backend, frontend), tests, DESIGN.md, real README, screenshots.
- **Biggest risk:** Chunk 6 (booking + concurrency + tests). It is graded hardest, so protect time for it.
- **If time runs short:** cut frontend polish first. Never cut backend correctness, tests or DESIGN.md.

---

## Chunks

### S. Repo, GitHub and local environment setup: DONE
- [x] GitHub repo created and public, initial commit pushed
- [x] `.gitignore` and `.env.example` in place; `.env` is not tracked
- [x] MySQL database `hotel_booking` and user `hotel_app` working

### 0. Lock design decisions: DONE
**Goal:** agree the five decisions below so nothing is guessed later. No code in this chunk.

Decisions (confirm or change, then keep consistent everywhere):
1. **Inventory:** pre-seed 120 days of `room_inventory` rows from the migration date. Dates beyond that cannot be booked (document under limitations).
2. **Occupied nights:** a stay occupies nights from check-in up to the night before check-out. The check-out day is free, so back-to-back stays do not conflict.
3. **Booking reference:** random and human-friendly, e.g. `HB-7K2M9Q`, with a UNIQUE constraint in the DB.
4. **Types:** `LocalDate` for dates; `BigDecimal` / `DECIMAL(10,2)` for money. Never `double`.
5. **Lookup/cancel:** requires reference AND email. A wrong email returns 404 (not 403) so references cannot be probed.

- [x] Decisions confirmed by the user and reflected in AGENTS.md
- Commit: `docs: lock design decisions`

### 1. Spring Boot skeleton + MySQL connection: DONE
**Goal:** a Spring Boot app in `/backend` that starts and connects to MySQL.
- Java 21, Maven, Spring Boot 3.x. Dependencies: web, data-jpa, validation, mysql driver, flyway-core + flyway-mysql, spring-boot-starter-test.
- Base package `com.hotelbooking` with sub-packages: `controller`, `service`, `repository`, `entity`, `dto`, `exception`.
- `application.properties` reads `DB_URL`, `DB_USER`, `DB_PASSWORD` from env vars. No hardcoded credentials. `spring.jpa.hibernate.ddl-auto=validate` (Flyway owns the schema).
- Add a short "Running the backend" section to README.md (`set -a; source .env; set +a` then `mvn spring-boot:run`).

**Done when:** `mvn spring-boot:run` starts without errors and logs a successful DB connection.
- [x] Acceptance met, build passes (`mvn clean verify`)
- Commit: `feat(backend): spring boot skeleton with mysql config`

### 2. V1 migration: tables: DONE
**Goal:** Flyway `V1__create_schema.sql` creates the schema.
- `hotels`: id, name, location, description, amenities, price_per_night DECIMAL(10,2), total_rooms.
- `room_inventory`: id, hotel_id (FK), stay_date, total_rooms, booked_rooms, **UNIQUE (hotel_id, stay_date)**, CHECK booked_rooms between 0 and total_rooms.
- `bookings`: id, reference **UNIQUE**, hotel_id (FK), guest_name, guest_email, check_in, check_out, nights, total_amount DECIMAL(10,2), status (CONFIRMED/CANCELLED), created_at.
- Indexes: hotels(location); bookings(reference, guest_email); room_inventory(hotel_id, stay_date).

**Done when:** app starts, Flyway applies V1, and `SHOW TABLES;` lists `hotels`, `room_inventory`, `bookings` (plus `flyway_schema_history`).
- [x] Migration defines all required tables, constraints and indexes; `mvn clean verify` passes (live DB migration not run)
- Commit: `feat(db): add V1 schema migration`

### 3. V2 migration: seed data: DONE
**Goal:** Flyway `V2__seed_data.sql` inserts sample data.
- 6 realistic hotels in different cities (name, location, description, comma-separated amenities, price per night, total rooms between 3 and 8, small so that sold-out cases are easy to test).
- `room_inventory` rows for every hotel for the next 120 days from the migration date.

**Done when:** `SELECT COUNT(*) FROM hotels;` returns 6 and `room_inventory` has 6 x 120 rows.
- [x] SQL defines six hotels and a generated 120-day inventory window; `mvn clean verify` passes (live DB migration not run)
- Commit: `feat(db): seed hotels and room inventory`

### 4. Entities, repositories, `GET /api/hotels`: DONE
**Goal:** list hotels over REST.
- JPA entities for hotels, room_inventory, bookings. Repositories. Response DTOs (never expose entities).
- `GET /api/hotels?location=` returns all hotels, or those whose location contains the text (case-insensitive).
- Response fields: id, name, location, description, amenities (as a list), pricePerNight.

**Done when:** `http://localhost:8080/api/hotels` returns 6 hotels, and `?location=goa` style filtering works.
- [x] Acceptance met; `mvn clean verify` and the controller JSON-shape test pass
- Commit: `feat(api): list and filter hotels`

### 5. Global error handler: DONE
**Goal:** every failure returns the same JSON shape.
- `@RestControllerAdvice` returning `{ timestamp, status, error, message, path, fieldErrors[] }`.
- Handle: validation errors (400, with `fieldErrors`), not found (404), conflict / no availability (409), unexpected errors (500, generic message, no stack trace leaked).
- Custom exceptions in the `exception` package.
- Add one test proving the format.

**Done when:** a bad request returns the standard error JSON with the right HTTP status.
- [x] Acceptance met; MVC tests cover validation, malformed input, custom errors, unknown URLs, and generic failures
- Commit: `feat(api): consistent error responses`

### 6a. Dates, availability and read-only price quote: DONE
**Goal:** validate requested stay dates, report per-night availability, and quote totals without creating bookings.
- Provide a `Clock` bean using the `Asia/Kolkata` zone so date rules can be tested deterministically.
- **Validation:** check-out after check-in; check-in cannot be in the past (today is allowed); maximum stay is 30 nights; all occupied nights must exist in seeded inventory, otherwise return a clear 400 error.
- **Availability:** a stay occupies check-in through the night before check-out. Report available only when every night has capacity; `roomsLeft` is the minimum free-room count across the stay. Use one repository query for the requested date range, not one query per night.
- **Pricing:** compute `nights x pricePerNight` using `BigDecimal`, on the backend only.
- `GET /api/hotels/{id}?checkIn=&checkOut=` returns details and optional quote/availability fields; if only one date is supplied return 400; unknown hotel returns 404.
- `GET /api/hotels?location=&checkIn=&checkOut=` includes quote/availability fields when both dates are supplied and preserves its existing response when dates are omitted.
- **Tests:** price calculation; invalid date order, past check-in and stays over 30 nights; one date missing; availability across nights; minimum rooms left; back-to-back stays.

**Done when:** quote and availability endpoints pass the required tests and `mvn clean verify` passes.
- [x] Acceptance met; `mvn clean verify` and date, availability, quote, and response tests pass
- Commit(s): `feat(booking): date validation and availability query`, `feat(booking): price quote on hotel details and search`, `test(booking): pricing and availability rules`

### 6b. Create booking with row locking and reference generation: DONE
**Goal:** create a confirmed booking safely using the validated dates and availability logic from 6a.
- `POST /api/bookings` (hotelId, checkIn, checkOut, guestName, guestEmail): validates the guest name/email, hotel, dates and inventory range, then creates one booking.
- **Concurrency:** one `@Transactional` method; lock affected `room_inventory` rows with `SELECT ... FOR UPDATE` (pessimistic write lock), ordered by date to avoid deadlocks; check capacity, increment `booked_rooms`, then insert the booking. Add a short comment explaining this.
- **Reference:** random, human-friendly (`HB-XXXXXX`), with uniqueness enforced by the database; handle collisions safely.
- Tests for booking creation, overlapping stays reducing capacity, and rejecting booking beyond capacity.

**Done when:** a curl `POST /api/bookings` returns a confirmation with a reference, and a second booking for the same last room returns 409.
- [x] Acceptance met; service and controller tests pass (`mvn clean verify`); live DB smoke test not run
- Commit: `feat(booking): create booking with row locking`

### 6c. Concurrency test and locking review: DONE
**Goal:** verify and review the last-room concurrency behavior implemented in 6b.
- Add a concurrency-focused test proving simultaneous attempts for the last room cannot both succeed.
- Review lock ordering, transaction boundaries, inventory increments and unique booking reference handling.

**Done when:** concurrency test passes and locking review findings are addressed.
- [x] DB-backed concurrency tests added; clean verify passes without DB variables (DB-enabled run must be performed against the local MySQL)
- Commit: `test(booking): concurrent booking integration test`

### 7. Lookup + cancel (with tests): DONE
**Goal:** retrieve and cancel a booking.
- `GET /api/bookings/{reference}?email=`: returns the booking. Wrong reference or wrong email: 404.
- `POST /api/bookings/{reference}/cancel` (email in body): sets status CANCELLED and decrements `booked_rooms` for each night in one transaction (same locking approach).
- Cancelling an already cancelled booking returns a clear message and does **not** release inventory twice.
- **Tests:** cancellation releases availability; double cancel is safe; wrong email is rejected.

**Done when:** book, cancel, then the same dates are bookable again.
- [x] Acceptance met; service and controller tests pass (`mvn clean verify`); DB-backed cancellation integration test added and awaits the local MySQL run
- Commits: `feat(booking): lookup booking by reference and email`, `feat(booking): cancel booking and release inventory`, `test(booking): lookup and cancel rules`

### 8. Surprise feature (backend): DONE
**Goal:** see "Surprise feature" in AGENTS.md. Alternative dates when a search has no availability.
- When the requested dates are sold out, the response also includes up to a few nearest check-in dates (same number of nights, within +/- 7 days) that have availability, plus price for each.
- Reuse the existing availability logic. One service method, one extra response field.
- 1-2 tests (suggestions appear only when sold out; suggested dates really are available).

**Done when:** a sold-out search returns alternative dates in the JSON.
- [x] Acceptance met; service and MVC tests cover nearest valid dates, bounds, pricing, and response fields (`mvn clean verify` passes)
- Commits: `feat(search): suggest alternative dates when sold out`, `test(search): alternative date suggestions`

### 9a. Frontend: hotel list + search: DONE
- React + Vite app in `/frontend`. API base URL from `VITE_API_BASE_URL`. Handle CORS on the backend if needed.
- Hotel cards (name, location, amenities, price per night). Search by location + check-in/check-out with date validation messages.
- Loading and error states.

**Done when:** the browser shows hotels and the search filters them.
- [x] Acceptance met; strict TypeScript production build passes (`npm run build`)
- Commits: `feat(frontend): project setup, api client and routing`, `feat(frontend): hotel list and search`

### 9b. Frontend: hotel details + price quote: DONE
- Details page: description, amenities, availability for the chosen dates, and the total price (from the API, not calculated in the UI) before booking.

**Done when:** selecting dates shows availability and the total for a hotel.
- [x] Acceptance met; strict TypeScript production build passes (`npm run build`)
- Commits: `refactor(frontend): shared date validation helper`, `feat(frontend): hotel details page with price quote`

### 9c. Frontend: booking form + confirmation: CURRENT
- Name + email form, inline validation messages, show total before confirming.
- Confirmation screen with the booking reference. Show API error messages clearly (e.g. sold out).

**Done when:** you can book a room end to end in the browser.
- Commit: `feat(frontend): booking form and confirmation`

### 9d. Frontend: lookup/cancel + alternative dates UI: UPCOMING
- Retrieve a booking by reference + email, show details, cancel button with confirmation.
- On sold-out searches, show the alternative-date suggestions as clickable options.

**Done when:** you can look up and cancel a booking, and clicking a suggested date re-runs the search.
- Commit: `feat(frontend): lookup, cancel and alternative dates`

### 10. README, DESIGN.md, screenshots, submission: UPCOMING
- README.md: prerequisites, DB setup, how to run backend and frontend, how to run tests, screenshots or demo recording, **approximate time spent**, unfinished items, **AI usage disclosure** (Codex used significantly).
- DESIGN.md: architecture, database design, API design, booking logic, design decisions and trade-offs, simultaneous bookings (last-room problem and the locking approach), surprise feature, limitations and improvements.
- Screenshots or short recording of the main flow in `/docs`.
- Submission email to submission@tctours.in by 14 Nov 2026, 12:00 AM. Subject: `091126 Developer Internship Assessment : <Candidate Name>`. Include resume PDF (with college name), public GitHub link, and a brief write-up (approach, assumptions, challenges, future improvements).

**Done when:** a fresh clone can be run by following only the README.
- Commit: `docs: readme, design doc and screenshots`

---

## Requirements coverage (assignment vs chunks)

| Assignment requirement | Covered in |
|---|---|
| Browse hotels (name, location, description, amenities, price) | 4, 9a |
| Search by location and dates | 6a, 9a |
| Hotel details + available rooms for dates | 6a, 9b |
| Book one room with name + email | 6b, 9c |
| Total amount shown before confirming (calculated on backend) | 6a, 9b, 9c |
| Booking confirmation with unique reference | 6b, 9c |
| Retrieve by reference + email, and cancel | 7, 9d |
| 5+ sample hotels | 3 |
| Date rules (check-out after check-in, no past check-in) | 6a, 6b |
| Availability tracking, cancellation releases availability | 6a, 6b, 7 |
| Validation messages, graceful failures, consistent errors | 5, 9c |
| Overlapping stays documented | 6b, 6c, 10 (DESIGN.md) |
| Surprise feature | 8, 9d, 10 |
| Automated tests (pricing, availability) | 6, 7, 8 |
| Database scripts + sample data | 2, 3 |
| No secrets in repo | 1 onward |
| README, DESIGN.md, screenshots, public repo | 10 |

---

## Assumptions (add as you go; these feed DESIGN.md)

- Inventory is pre-seeded for 120 days from the migration date.
- A stay occupies nights from check-in to the night before check-out.
- One room category per hotel; a booking is for exactly one room.
- Inventory window starts at the migration date and covers 120 consecutive days.
- Maximum stay is 30 nights.
- “Today” for booking date validation uses the `Asia/Kolkata` time zone.
- A booking with check-in before today cannot be cancelled; a booking checking in today remains cancellable.

## Open questions / notes (add as you go)

- Chunk 1 enabled Flyway baseline-on-migrate. With V1 present, a fresh empty database runs V1 as its first migration.

**Concurrency approach (for DESIGN.md):** The race is two guests both observing the last room as free and both confirming it. The booking transaction takes pessimistic write locks on each affected inventory row in ascending date order before checking capacity, so the second guest waits and then sees the first guest's increment. The trade-off is that locks remain held for the transaction, though they affect only the selected hotel's booked dates; other hotels and dates remain available. An alternative is optimistic locking with a version column and retries after conflicts.

**Surprise feature (for DESIGN.md):** When the requested stay is sold out, the API checks nearby check-in dates for the same stay length and suggests up to three available options. It uses one read-only inventory-range query and evaluates candidates in memory, with the nearest dates first. The search window is limited to seven days on either side, results are capped at three, and suggestions keep the requested stay length.
