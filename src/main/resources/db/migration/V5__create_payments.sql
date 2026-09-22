CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    amount BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'UNPAID',
    qr_reference VARCHAR(255) NOT NULL UNIQUE,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)
);

CREATE INDEX idx_payments_status ON payments(status);