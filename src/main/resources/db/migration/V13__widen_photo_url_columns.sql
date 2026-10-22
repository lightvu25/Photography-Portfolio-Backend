-- V13: Widen photo url and public_id columns to avoid truncation / 409 conflict
-- when Cloudinary transformations (such as crops) or long filenames are used.

ALTER TABLE photos
    ALTER COLUMN image_url TYPE VARCHAR(1024),
    ALTER COLUMN thumbnail_url TYPE VARCHAR(1024),
    ALTER COLUMN cloudinary_public_id TYPE VARCHAR(1024);
