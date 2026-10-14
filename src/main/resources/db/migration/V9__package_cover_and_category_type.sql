ALTER TABLE pricing_packages
    ADD COLUMN cover_photo_id BIGINT,
    ADD CONSTRAINT fk_package_cover_photo FOREIGN KEY (cover_photo_id)
        REFERENCES photos(id) ON DELETE SET NULL;

ALTER TABLE categories
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'PORTFOLIO';

CREATE INDEX idx_categories_type ON categories(type);
