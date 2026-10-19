-- V11: reserved "Admin" category rules + crop metadata + profile photo link.

-- 1. Rename the reserved internal category (slug stays 'admin-noi-bo' — it is
--    protected in CategoryService). Display name becomes simply "Admin".
UPDATE categories SET name = 'Admin' WHERE slug = 'admin-noi-bo';

-- 2. The Admin category is exclusive: a photo carrying it may have no other
--    categories. Existing mixed assignments keep ONLY the reserved link —
--    their public (portfolio) associations are removed so Admin photos never
--    leak into the public gallery.
DELETE FROM photo_categories pc
USING categories reserved
WHERE reserved.slug = 'admin-noi-bo'
  AND EXISTS (
      SELECT 1 FROM photo_categories pc2
      WHERE pc2.photo_id = pc.photo_id AND pc2.category_id = reserved.id
  )
  AND pc.category_id <> reserved.id;

-- 3. Crop metadata — fractions of the stored image (0..1) — plus the pristine
--    delivery URL so a crop never destroys the original Cloudinary URL.
ALTER TABLE photos
    ADD COLUMN crop_x DOUBLE PRECISION,
    ADD COLUMN crop_y DOUBLE PRECISION,
    ADD COLUMN crop_w DOUBLE PRECISION,
    ADD COLUMN crop_h DOUBLE PRECISION,
    ADD COLUMN original_image_url VARCHAR(1024);

-- 4. Self-introduction photo for the homepage About section. Only photos in
--    the reserved Admin category may be assigned (enforced in AdminSiteController).
ALTER TABLE site_settings
    ADD COLUMN profile_photo_id BIGINT,
    ADD CONSTRAINT fk_profile_photo FOREIGN KEY (profile_photo_id)
        REFERENCES photos(id) ON DELETE SET NULL;

-- Backfill only when the legacy pointer already references a photo that is
-- verifiably in the reserved category.
UPDATE site_settings s SET profile_photo_id = (
    SELECT p.id FROM photos p
    JOIN photo_categories pc ON pc.photo_id = p.id
    JOIN categories c ON c.id = pc.category_id
    WHERE p.cloudinary_public_id = s.profile_cloudinary_public_id
      AND c.slug = 'admin-noi-bo'
    LIMIT 1
)
WHERE s.profile_cloudinary_public_id IS NOT NULL;
