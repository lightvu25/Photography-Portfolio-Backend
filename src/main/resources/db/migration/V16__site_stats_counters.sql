ALTER TABLE site_settings
    ADD COLUMN clients_count    INT NOT NULL DEFAULT 100,
    ADD COLUMN concepts_count   INT NOT NULL DEFAULT 10,
    ADD COLUMN experience_years INT NOT NULL DEFAULT 2;
