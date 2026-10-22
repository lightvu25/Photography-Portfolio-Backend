# Bấu Chụp Choẹt (Hải Bấu Photography) — Backend

Production-ready Spring Boot 3.3 / Java 21 backend for **Bấu Chụp Choẹt**, powering portfolio management, package pricing, concurrent-safe booking holds, VietQR deposit generation, customer feedback moderation, and site settings.

## Features
- **Portfolio & Reserved `Admin` Category**: Manage photos, non-destructive Cloudinary crops (`crop_x/y/w/h` + `original_image_url`), and categories (`PORTFOLIO` and `PACKAGE`). The reserved `Admin` category (`slug = 'admin-noi-bo'`) is strictly isolated from public gallery/category endpoints and package cover photos, and serves exclusively as the source for the homepage About photo (`aboutPhoto`).
- **Booking System**: Concurrent-safe booking system with `MORNING` / `AFTERNOON` slots, atomic `FULL_DAY` booking, optional customer note (`note` / `"Lưu ý với thợ"`), Cloudflare Turnstile verification, and automated 30-minute `PENDING` holds.
- **Pricing Packages**: Official tiered packages by `groupSize` (1, 2, 3) and `sessionType` (`HALF_DAY`, `FULL_DAY`), managed via `/api/admin/pricing` and exposed publicly at `GET /api/pricing`.
- **Payment**: VietQR payload generation for deposit payments upon admin confirmation.
- **Sanitized Public Site Settings**: `GET /api/site` returns `PublicSiteSettingsResponse`, exposing public profile/social fields and a minimal `aboutPhoto` (`id`, `title`, `description`, `altText`, `imageUrl`) without internal Cloudinary public IDs or category metadata.

## Architecture & Tech Stack
- **Framework**: Java 21, Spring Boot 3.3.x
- **Database**: PostgreSQL (Supabase in production, Docker Compose / Testcontainers locally)
- **Migrations**: Flyway (`V1` through `V12`)
- **Security**: Spring Security + JWT + Cloudflare Turnstile + in-memory rate limiting
- **Documentation**: Swagger UI / OpenAPI 3 (`/swagger-ui.html`)

## Booking Concurrency & Behavior
1. **Database Unique Constraints**: `(booking_date, slot)` is uniquely constrained for active bookings (`PENDING` or `CONFIRMED`) via the partial unique index `uk_active_booking_slots`.
2. **Transactional Logic**: Slot validation and insert operations execute within a single transaction (`saveAndFlush`); constraint violations surface as HTTP `409 BOOKING_CONFLICT`.
3. **Full-Day Bookings**: A `FULL_DAY` booking inserts both `MORNING` and `AFTERNOON` `BookingSlot` rows atomically.
4. **30-Minute Hold & Auto-Expiration**: New bookings enter `PENDING` with `expires_at = now + 30 minutes`. A scheduled job (`@Scheduled`, every 60s) and on-demand slot checks transition expired `PENDING` bookings to `EXPIRED` and release their slots.
5. **Customer Notes**: `POST /api/bookings` accepts an optional `note` (`≤ 1000` chars), persisted on `bookings.note` and returned in `Booking` and `GET /api/bookings/{bookingCode}` (`BookingStatusResponse`).

## Database Migrations (`src/main/resources/db/migration`)
- `V1`–`V8`: Core tables (`admin_users`, `photos`, `categories`, `photo_categories`, `pricing_packages`, `bookings`, `booking_slots`, `payments`, `blocked_slots`, `feedbacks`, `site_settings`).
- `V9`: Package `cover_photo_id` and category `type` (`PORTFOLIO` / `PACKAGE`).
- `V10`–`V11`: Reserved `Admin` category (`slug = 'admin-noi-bo'`), non-destructive photo crop columns, and `site_settings.profile_photo_id`.
- `V12`: Adds `bookings.note`, clears any `Admin`-category photo from `pricing_packages.cover_photo_id`, syncs official pricing packages (`1.3M/2.1M`, `1.7M/2.5M`, `2.1M/2.9M`), and seeds the 5 marquee portfolio categories (`Kỉ yếu THCS`, `Kỉ yếu THPT`, `Tốt nghiệp đại học`, `Áo dài`, `Concept`) with `ON CONFLICT (slug) DO NOTHING`.

## Local Setup & Testing
1. Start local PostgreSQL:
   ```bash
   docker compose up -d
   ```
2. Configure environment variables (`.env` from `.env.example`):
   - Required: `JWT_SECRET`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `TURNSTILE_SECRET_KEY`, `TURNSTILE_SITE_KEY`, `ADMIN_INITIAL_PASSWORD`.
   - Set `TURNSTILE_ENABLED=false` for local development/testing.
3. Run application or tests (requires JDK 21 and Docker Desktop for Testcontainers):
   ```bash
   ./mvnw spring-boot:run
   ./mvnw clean test
   ```
