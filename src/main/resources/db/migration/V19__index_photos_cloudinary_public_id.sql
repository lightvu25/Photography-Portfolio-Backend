-- V19: Index on photos.cloudinary_public_id for fast idempotency lookup during
-- direct-upload metadata completion retries.
CREATE INDEX IF NOT EXISTS idx_photos_cloudinary_public_id ON photos(cloudinary_public_id);
