CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE photos (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    description TEXT,
    cloudinary_public_id VARCHAR(255) NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    thumbnail_url VARCHAR(255),
    alt_text VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE photo_categories (
    photo_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (photo_id, category_id),
    CONSTRAINT fk_photo FOREIGN KEY (photo_id) REFERENCES photos(id) ON DELETE CASCADE,
    CONSTRAINT fk_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

CREATE INDEX idx_photos_status ON photos(status);
CREATE INDEX idx_photos_is_featured ON photos(is_featured);
CREATE INDEX idx_photos_sort_order ON photos(sort_order);