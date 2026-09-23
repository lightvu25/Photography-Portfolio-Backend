# Hải Bấu Photography - Backend

This is the production-ready backend for "Hải Bấu Photography", a booking and portfolio platform.

## Features
- **Portfolio Management**: Manage photo gallery, categories, and image metadata.
- **Booking System**: Concurrent-safe booking system with slots (morning/afternoon), full-day blocking, and automated 30-min holds.
- **Payment**: VietQR payload generation for deposit payments.
- **Admin Dashboard**: Secure JWT-based admin access for managing site settings, pricing packages, feedback, and bookings.
- **Cloudinary Integration**: Direct image upload and storage with automatic hard-deletion.

## Architecture & Tech Stack
- **Framework**: Java 21, Spring Boot 3.3.x.
- **Database**: PostgreSQL (Supabase).
- **Migrations**: Flyway.
- **Security**: Spring Security + JWT + Cloudflare Turnstile.
- **Documentation**: Swagger UI / OpenAPI 3.

## Project Structure
The project uses a feature-based package structure for maintainability and separation of concerns:
- `auth`: Admin authentication, JWT handling.
- `booking`: Core reservation system, concurrency locking, and status transitions.
- `payment`: VietQR integration and deposit status.
- `gallery`: Photos and categories CRUD.
- `pricing`: Photography packages management.
- `site`: General photographer settings and bio.
- `feedback`: Customer testimonials.

## Booking Concurrency Strategy
The system prevents double bookings through:
1. **Database Unique Constraints**: `(booking_date, slot)` uniquely constrained for active bookings (`PENDING` or `CONFIRMED`) — the partial unique index `uk_active_booking_slots` is the final authority.
2. **Transactional Logic**: Both slot validation and insert operations execute within a single transaction; constraint violations surface as HTTP 409.
3. **Full-Day Bookings**: A `FULL_DAY` booking is one logical `Booking` that inserts both `MORNING` and `AFTERNOON` `BookingSlot` rows atomically — it either fully succeeds or fully rolls back.
4. **Auto-Expiration**: A scheduled job (`@Scheduled`, every 60s, enabled via `@EnableScheduling`) transitions stale `PENDING` bookings to `EXPIRED` and frees their slots.

## Booking Behavior
- **Sessions**: `MORNING` (07:00–11:30) and `AFTERNOON` (13:00–17:00). A day holds either two `HALF_DAY` bookings or one `FULL_DAY` booking.
- **Hold**: New bookings enter `PENDING` with `expires_at = now + 30 minutes`. While `PENDING`, the slot is occupied; a live pending hold also blocks re-booking at the database level.
- **State machine**: `PENDING → CONFIRMED | REJECTED | EXPIRED`, `CONFIRMED → COMPLETED | CANCELLED`. No other transitions are allowed; confirming an already-expired hold marks it `EXPIRED` and returns 409.
- **Availability**: `GET /api/availability?start=YYYY-MM-DD&end=YYYY-MM-DD` (public, params optional) returns per-day `morning`/`afternoon` status (`AVAILABLE` | `BOOKED` | `BLOCKED`) plus derived `fullDayAvailable`. `EXPIRED`, `REJECTED`, `CANCELLED`, and `COMPLETED` bookings do not block slots.
- **Blocked slots**: Admins manage them via `/api/admin/blocked-slots` (`GET`, `POST`, `DELETE /{id}`). Blocking a slot occupied by an active booking returns 409; customer bookings are never silently cancelled.

## Portfolio, Feedback & Site Settings
- **Photos**: Uploaded via `POST /api/admin/photos` (multipart `file` + optional `title`, `description`, `altText`, `sortOrder`, `categoryIds`). The image goes to Cloudinary (`haibau/photos`); `image_url`, `thumbnail_url` (derived `w_400,c_fill,q_auto` transformation), and `cloudinary_public_id` are stored. Lifecycle is `DRAFT → PUBLISHED` via `POST /api/admin/photos/{id}/publish|unpublish`. `DELETE` destroys the Cloudinary asset first — if that fails the DB row is kept so the `public_id` is never lost (HTTP 502); a "not found" asset counts as already-deleted.
- **Categories**: Many-to-many with photos (`photo_categories`, FK cascade). Admin CRUD under `/api/admin/categories`; slugs are auto-generated from the name when omitted. Public list at `GET /api/categories`.
- **Public gallery**: `GET /api/gallery` (published only, `sortOrder` ascending, categories included), `GET /api/gallery/featured`.
- **Feedback**: Public `POST /api/feedback` (name + content, starts unpublished) and `GET /api/feedback` (published only). Admin moderation under `/api/admin/feedbacks` (`GET`, `PUT` incl. `isPublished`, `POST /{id}/image` for an optional Cloudinary screenshot in `haibau/feedback`, `DELETE` — also removes the Cloudinary asset).
- **Site settings**: Public `GET /api/site` returns only public fields (photographer name, bio, social URLs, images, deposit amount, equipment). Admin `GET`/`PUT /api/admin/site` upserts the single settings row. No secrets are exposed.

- **Payments**: confirming a booking creates a 500,000 VND deposit payment
  (`UNPAID`) with a VietQR image URL. Customers see it via
  `GET /api/bookings/{code}`; admins manage payments via `GET /api/admin/payments`
  and `POST /api/admin/payments/{id}/mark-paid`.
- **Rate limiting**: lightweight in-memory limits protect login, booking and
  feedback endpoints (HTTP 429). Suitable for the single-instance deployment.

All `/api/admin/**` endpoints require a JWT. Public endpoints (`/api/gallery`, `/api/categories`, `/api/feedback`, `/api/site`, `/api/availability`, `/api/bookings`) need no auth.

## Local Setup

### 1. Prerequisites
- JDK 21
- Docker Desktop (for local PostgreSQL and for Testcontainers-based tests)
- Maven is not required separately; use the included `./mvnw` wrapper.

### 2. Start PostgreSQL (local development)
A `docker-compose.yml` is included that runs PostgreSQL 15 locally:

```bash
docker compose up -d        # start
docker compose ps           # check status / health
docker compose logs -f postgres   # view logs
docker compose down         # stop (data persists in the named volume)
```

This creates a database `haibau` on `localhost:5432` with user/password
`postgres`/`postgres` (overridable via `POSTGRES_*` variables in `.env`),
matching the defaults in `application.yml`.

### 3. Environment Variables
Copy `.env.example` to `.env` and fill out your credentials:
```bash
cp .env.example .env
```

`.env` is gitignored. Docker Compose reads it automatically. The Spring Boot
app reads real environment variables — export them (or configure them in your
IDE run configuration) before running:

```bash
export $(grep -v '^#' .env | xargs)   # Git Bash / Linux
./mvnw spring-boot:run
```

Required variables with no defaults: `JWT_SECRET`, `CLOUDINARY_CLOUD_NAME`,
`CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `TURNSTILE_SECRET_KEY`,
`TURNSTILE_SITE_KEY`, `ADMIN_INITIAL_PASSWORD`. For production, the `BANK_*`
variables define the recipient account for deposit QR codes.

Other notable variables:
- `TURNSTILE_ENABLED` (default `true`) — set `false` only for local
  development/tests so bookings don't require a live Cloudflare challenge.
  In production keep it `true`; invalid/missing tokens are rejected.
- `FRONTEND_URL` — comma-separated CORS origins, e.g.
  `http://localhost:3000,https://your-app.vercel.app`.
- `PORT` — server port (Render sets this automatically).
- `ADMIN_EMAIL` / `ADMIN_INITIAL_PASSWORD` — the admin account is seeded on
  startup if missing; the app will not start without `ADMIN_INITIAL_PASSWORD`.

### 4. Database Migrations
Flyway runs automatically on startup (`spring.flyway.enabled=true`), applying
the migrations in `src/main/resources/db/migration`. Hibernate then validates
the schema (`ddl-auto: validate`).

### 5. Run Application
```bash
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

## Swagger / OpenAPI
Once running, the API documentation is available at:
`http://localhost:8080/swagger-ui.html`

## Testing
The integration tests use **Testcontainers**, which spins up a disposable
PostgreSQL container automatically — no local database needed (but Docker must
be running):

```bash
./mvnw clean test             # Windows: .\mvnw.cmd clean test
```

### Database environments
- **Local development**: `docker compose up -d` → persistent PostgreSQL on
  `localhost:5432` (data survives restarts in a named volume).
- **Automated tests**: Maven → Testcontainers → a throwaway PostgreSQL
  container created per test run.
- **Production**: Render-hosted app → Supabase PostgreSQL, configured via the
  `DATABASE_*` environment variables.

## Deployment
This backend is designed to be easily deployed to **Render**:
1. Connect the GitHub repository to a Render Web Service.
2. Select Java / Maven environment.
3. Set the root directory to `backend`.
4. Inject the environment variables from the `.env` file into the Render configuration.
5. Flyway migrations will execute on startup automatically.
