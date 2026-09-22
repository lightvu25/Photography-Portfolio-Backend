# Hải Bấu Photography - Backend

This is the production-ready backend for "Hải Bấu Photography", a booking and portfolio platform.

## Features
- **Portfolio Management**: Manage photo gallery, categories, and image metadata.
- **Booking System**: Concurrent-safe booking system with slots (morning/afternoon), full-day blocking, and automated 30-min holds.
- **Payment**: VietQR payload generation for deposit payments.
- **Admin Dashboard**: Secure JWT-based admin access for managing site settings, pricing packages, feedback, and bookings.
- **Cloudinary Integration**: Direct image upload and storage with automatic hard-deletion.

## Architecture & Tech Stack
- **Framework**: Java 17, Spring Boot 3.3.x (Java 17 is used for environment compatibility; code is compatible with 21).
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
1. **Database Unique Constraints**: `(booking_date, slot)` uniquely constrained for active bookings (`PENDING` or `CONFIRMED`).
2. **Transactional Logic**: Both slot validation and insert operations execute within a single transaction.
3. **Full-Day Bookings**: Full-day reservations insert two locked slots (Morning and Afternoon) atomically.
4. **Auto-Expiration**: A scheduled job (`@Scheduled`) evicts `PENDING` bookings that exceed the 30-minute hold limit.

## Local Setup

### 1. Prerequisites
- Java 17+
- PostgreSQL
- Maven

### 2. Environment Variables
Copy `.env.example` to `.env` and fill out your credentials:
```bash
cp .env.example .env
```

Ensure you configure the `DATABASE_URL` and `CLOUDINARY_*` keys. 
For production, the `BANK_*` variables define the recipient account for deposit QR codes.

### 3. Database Migrations
Flyway is configured to run automatically. Ensure the target PostgreSQL database exists before starting.

### 4. Run Application
```bash
./mvnw spring-boot:run
```

## Swagger / OpenAPI
Once running, the API documentation is available at:
`http://localhost:8080/swagger-ui.html`

## Testing
To run the automated test suite:
```bash
./mvnw test
```

## Deployment
This backend is designed to be easily deployed to **Render**:
1. Connect the GitHub repository to a Render Web Service.
2. Select Java / Maven environment.
3. Set the root directory to `backend`.
4. Inject the environment variables from the `.env` file into the Render configuration.
5. Flyway migrations will execute on startup automatically.
