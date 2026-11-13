CREATE TABLE hotels (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    location VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    amenities TEXT NOT NULL,
    price_per_night DECIMAL(10, 2) NOT NULL,
    total_rooms INT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_hotels_location (location)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE room_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hotel_id BIGINT NOT NULL,
    stay_date DATE NOT NULL,
    total_rooms INT NOT NULL,
    booked_rooms INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_room_inventory_hotel_stay_date UNIQUE (hotel_id, stay_date),
    CONSTRAINT chk_room_inventory_booked_rooms CHECK (booked_rooms BETWEEN 0 AND total_rooms),
    CONSTRAINT fk_room_inventory_hotel FOREIGN KEY (hotel_id) REFERENCES hotels (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference VARCHAR(9) NOT NULL,
    hotel_id BIGINT NOT NULL,
    guest_name VARCHAR(150) NOT NULL,
    guest_email VARCHAR(254) NOT NULL,
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    nights INT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status ENUM('CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_bookings_reference UNIQUE (reference),
    INDEX idx_bookings_reference_guest_email (reference, guest_email),
    CONSTRAINT fk_bookings_hotel FOREIGN KEY (hotel_id) REFERENCES hotels (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
