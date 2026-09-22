CREATE TABLE blocked_slots (
    id BIGSERIAL PRIMARY KEY,
    blocked_date DATE NOT NULL,
    slot VARCHAR(50) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_blocked_slots UNIQUE (blocked_date, slot)
);