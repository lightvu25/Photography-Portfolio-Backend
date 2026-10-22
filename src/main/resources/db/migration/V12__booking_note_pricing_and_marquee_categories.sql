-- V12: Add booking customer note ("Lưu ý với thợ"), sync official pricing
-- packages (1.3M/2.1M, 1.7M/2.5M, 2.1M/2.9M), seed the 5 marquee portfolio
-- categories without deleting existing categories, and clear any package cover
-- photo that references the reserved Admin category ('admin-noi-bo').

-- 1. Add optional customer note column to bookings
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS note TEXT;

-- 2. Ensure no pricing package uses a photo from the reserved Admin category
--    as its public cover image.
UPDATE pricing_packages
SET cover_photo_id = NULL
WHERE cover_photo_id IN (
    SELECT pc.photo_id
    FROM photo_categories pc
    JOIN categories c ON c.id = pc.category_id
    WHERE c.slug = 'admin-noi-bo'
);

-- 3. Update existing pricing packages by (group_size, session_type) to the
--    confirmed official rates:
--    - 1 person: HALF_DAY = 1,300,000 VND | FULL_DAY = 2,100,000 VND
--    - 2 people: HALF_DAY = 1,700,000 VND | FULL_DAY = 2,500,000 VND
--    - 3 people: HALF_DAY = 2,100,000 VND | FULL_DAY = 2,900,000 VND
UPDATE pricing_packages
SET price = 1300000, updated_at = CURRENT_TIMESTAMP
WHERE group_size = 1 AND session_type = 'HALF_DAY';

UPDATE pricing_packages
SET price = 2100000, updated_at = CURRENT_TIMESTAMP
WHERE group_size = 1 AND session_type = 'FULL_DAY';

UPDATE pricing_packages
SET price = 1700000, updated_at = CURRENT_TIMESTAMP
WHERE group_size = 2 AND session_type = 'HALF_DAY';

UPDATE pricing_packages
SET price = 2500000, updated_at = CURRENT_TIMESTAMP
WHERE group_size = 2 AND session_type = 'FULL_DAY';

UPDATE pricing_packages
SET price = 2100000, updated_at = CURRENT_TIMESTAMP
WHERE group_size = 3 AND session_type = 'HALF_DAY';

UPDATE pricing_packages
SET price = 2900000, updated_at = CURRENT_TIMESTAMP
WHERE group_size = 3 AND session_type = 'FULL_DAY';

-- 4. Seed the 5 confirmed marquee portfolio categories if not already present.
--    Existing categories (Kỷ Yếu, Tốt Nghiệp, Nàng Thơ, Couple, etc.) remain intact.
INSERT INTO categories (name, slug, sort_order, type)
VALUES
    ('Kỉ yếu THCS', 'ki-yeu-thcs', 10, 'PORTFOLIO'),
    ('Kỉ yếu THPT', 'ki-yeu-thpt', 11, 'PORTFOLIO'),
    ('Tốt nghiệp đại học', 'tot-nghiep-dai-hoc', 12, 'PORTFOLIO'),
    ('Áo dài', 'ao-dai', 13, 'PORTFOLIO'),
    ('Concept', 'concept', 14, 'PORTFOLIO')
ON CONFLICT (slug) DO NOTHING;
