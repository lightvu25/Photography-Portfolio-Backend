CREATE TABLE site_settings (
    id BIGSERIAL PRIMARY KEY,
    photographer_name VARCHAR(255) NOT NULL,
    experience VARCHAR(255),
    bio TEXT,
    facebook_url VARCHAR(255),
    instagram_url VARCHAR(255),
    messenger_url VARCHAR(255),
    hero_image_url VARCHAR(255),
    hero_cloudinary_public_id VARCHAR(255),
    profile_image_url VARCHAR(255),
    profile_cloudinary_public_id VARCHAR(255),
    deposit_amount BIGINT NOT NULL DEFAULT 500000,
    equipment JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);