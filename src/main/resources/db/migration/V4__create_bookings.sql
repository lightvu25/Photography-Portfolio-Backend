CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    booking_code VARCHAR(100) NOT NULL UNIQUE,
    package_id BIGINT NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    instagram VARCHAR(255),
    address TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_package FOREIGN KEY (package_id) REFERENCES pricing_packages(id)
);

CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_expires_at ON bookings(expires_at);

CREATE TABLE booking_slots (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    booking_date DATE NOT NULL,
    slot VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_booking_slot FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
);

CREATE INDEX idx_booking_slots_date ON booking_slots(booking_date);
CREATE INDEX idx_booking_slots_status ON booking_slots(status);

-- Partial index to enforce unique slot only for active bookings
CREATE UNIQUE INDEX uk_active_booking_slots ON booking_slots(booking_date, slot) 
WHERE status IN ('PENDING', 'CONFIRMED');