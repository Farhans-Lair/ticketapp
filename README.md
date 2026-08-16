# TicketVerse

A BookMyShow-style event ticketing platform. Started as an exercise in learning Spring Boot properly — the original version was a Node/Express + Sequelize app (see the companion `Ticket-Booking-Application-2` repo) — and has since grown well past that: full seat categories, coupons, QR check-in, wishlist/waitlist, tiered refunds, organizer payouts, the works. Backend is Spring Boot 3.2.4 / Java 17, frontend is plain HTML/CSS/JS (no framework), MySQL for the database, deployed on AWS via Terraform.

---

## Table of Contents

- [Why Spring Boot](#why-spring-boot)
- [Project Overview](#project-overview)
- [Architecture](#architecture)
- [Stack](#stack)
- [Features](#features)
- [Auth, briefly](#auth-briefly)
- [Running locally](#running-locally)
- [Environment Variables](#environment-variables)
- [Database & Migrations](#database--migrations)
- [API Reference](#api-reference)
- [Frontend Pages](#frontend-pages)
- [Testing](#testing)
- [Deploying](#deploying)
- [Known issues / not yet cleaned up](#known-issues--not-yet-cleaned-up)
- [License](#license)

---

## Why Spring Boot

Migrated off Express to get proper hands-on experience with the Java/Spring ecosystem rather than just reading about it. The existing Controller → Service → Model → DB layout in the Express app mapped over cleanly to Controller → Service → Repository → Entity. Getting to parity took a few rounds — there's a real story in the codebase's own comments about `AccessControl` replacing ten near-identical inline admin-role checks that used to be duplicated across `EventController`, `RevenueController`, `EventCategoryController`, `OrganizerController` and `PayoutController` (see `security/AccessControl.java`) — but at this point Spring Boot does more than the original Express app ever did.

## Project Overview

| Role | Capabilities |
|---|---|
| **User** | Browse/search events, book seats, apply coupons, pay via Razorpay, manage profile, view bookings, cancel with tiered refunds, download QR tickets/invoices, review, wishlist, waitlist |
| **Organizer** | Register (admin-approved), create & submit events for moderation, configure seat categories/pricing, scan tickets at check-in, set cancellation policy, view revenue, request payouts |
| **Admin** | Moderate events (approve/reject/revoke), manage organizers, manage categories, feature/unfeature events, process payouts, view platform revenue |

## Architecture

```
ticketapp/
├── src/main/java/com/ticketapp/
│   ├── TicketAppApplication.java     # @SpringBootApplication, @EnableScheduling, @EnableAsync
│   ├── controller/                   # 18 REST controllers
│   ├── service/                      # 20 service classes (business logic)
│   ├── repository/                   # 13 Spring Data JPA repositories
│   ├── entity/                       # 13 JPA entities
│   ├── dto/                          # Request/response DTOs (validated with Bean Validation)
│   ├── security/                     # JwtAuthFilter, JwtUtil, AuthenticatedUser, AccessControl
│   ├── config/                       # SecurityConfig, RateLimitFilter, CacheConfig, WebConfig,
│   │                                 #   CorrelationFilter, HttpsConfig, S3Config, JacksonConfig
│   ├── scheduler/                    # SeatHoldScheduler, EventReminderScheduler,
│   │                                 #   RefreshTokenCleanupScheduler
│   └── exception/                    # GlobalExceptionHandler + typed exceptions
├── src/main/resources/
│   ├── application.properties        # Dev defaults (ddl-auto=update, Flyway off)
│   ├── application-prod.properties   # Prod overrides (ddl-auto=validate, Flyway on)
│   ├── db/migration/                 # Flyway scripts V1–V11 (source of truth in prod)
│   ├── fonts/                        # Fonts embedded in generated PDFs (PDFBox)
│   └── static/                       # Frontend — HTML/CSS/JS, baked into the jar at build time
├── src/test/java/                    # Spring Boot context smoke test
├── db/                                # Legacy hand-written SQL from the pre-Flyway/Node era —
│                                      #   not used by the running app; kept for historical reference
│                                      #   only. The real schema lives in src/main/resources/db/migration.
├── terraform/                        # AWS infrastructure (VPC + EC2 ASG + ALB + RDS + S3 + CloudWatch)
├── Dockerfile                        # Two-stage build: maven:3.9.6-eclipse-temurin-17 → eclipse-temurin:17-jre-alpine
└── docker-compose.yml                # MySQL + Redis + backend for local dev
```

The backend is a single Spring Boot application serving both the JSON API and the static frontend (baked into the jar under `static/`, no separate frontend build/deploy step). There is no microservices split.

### Request flow (production)

```
Browser → ALB (HTTPS, self-signed cert, TLS 1.3) → EC2 Auto Scaling Group
                                                        ├─ nginx (rate limiting, :8080) → Spring Boot (127.0.0.1:8081)
                                                        ├─ RDS MySQL primary (Multi-AZ) + read replica
                                                        ├─ S3 (ticket/invoice/event-image PDFs & images)
                                                        └─ SSM Parameter Store (secrets, fetched at boot)
```

nginx sits in front of the Spring Boot container on every instance, listening on the port the ALB target group talks to (8080) and reverse-proxying to Spring Boot, which binds only to `127.0.0.1:8081` — not directly reachable from the ALB or the internet. This exists specifically to get per-client-IP rate limiting without paying for AWS WAF; see `terraform/nginx-rate-limit.conf`.

Three background jobs run inside the same JVM via Spring's `@Scheduled`:
- **Seat-hold sweeper** — configurable interval (default 60s), releases expired seat holds.
- **Event-reminder scheduler** — configurable cron (default daily 09:00), emails users with paid bookings for events happening soon.
- **Refresh-token cleanup** — configurable interval (default daily), purges expired/revoked refresh token rows.

## Stack

Spring Boot 3.2.4, Java 17, Spring Security, Spring Data JPA/Hibernate + Flyway, Bucket4j (rate limiting), JJWT 0.12.3, PDFBox 2.0.30, ZXing 3.5.3 (QR), AWS SDK v2 (S3), Razorpay Java SDK, Twilio SDK, Spring Mail, Spring Data Redis, Caffeine, Lombok, Logstash Logback Encoder (structured JSON logs), Micrometer Tracing (Brave). Frontend is plain HTML/CSS/JS — DM Sans + Playfair Display, dark theme, three separate dashboard themes for user/organizer/admin. MySQL 8 (RDS in prod, Multi-AZ with a read replica), Redis for OTP storage only. Infrastructure is all Terraform — VPC across 2 AZs, ALB, EC2 ASG, RDS, S3, ECR, SSM Parameter Store for secrets, CloudWatch alarms. CI/CD is GitHub Actions with OIDC (no stored AWS keys).

## Features

### Authentication & Sessions
- Email OTP auth for both signup and login (user and organizer), each gated behind a Bucket4j-limited request/verify pair.
- Three-token JWT model — access (15 min), refresh (7 days), session (30 days) — each with its own secret (`jwt.access-secret`, `jwt.refresh-secret`, `jwt.session-secret`), so a leaked secret for one token type can never be used to forge another type.
- Refresh tokens are rotated on every `/auth/refresh` call; replaying an already-rotated (revoked) token is treated as a stolen-token signal and revokes every refresh token in that session (`RefreshTokenService.rotate`).
- Refresh/session tokens are returned in the response body and stored in `sessionStorage` per browser tab on the frontend (cookies are also set as a fallback) — cookies are scoped to the browser, not the tab, which caused two logged-in accounts in two tabs of the same browser to interfere with each other before this was fixed.
- A dedicated `RefreshTokenCleanupScheduler` purges expired/revoked refresh-token rows on a configurable interval (default daily) so the table doesn't grow unbounded.

### Events & Discovery
- Admin-created events publish immediately; organizer-submitted events require explicit `submit` and then admin `approve`/`reject`/`revoke`.
- Featured and Trending event endpoints, each cached separately in Caffeine (60s and 120s TTL respectively).
- Dynamic event categories (admin CRUD), with a 300s Caffeine cache on the public read.
- City/keyword search and filtered search (`/search`, `/search/events`, `/search/cities`).

### Seats & Booking
- Seat category tiers with per-seat pricing, configured per event by the organizer/admin (`/seats/{eventId}/configure`).
- Pessimistic DB-row locking on seat holds so two people can never book the same seat in a race.
- A configurable seat-hold timer (default 60s sweep interval) automatically releases holds that aren't confirmed in time.
- Razorpay order creation + HMAC-SHA256 signature verification on `/payments/verify`, matching the original Node implementation's verification algorithm exactly.
- Coupon codes (percentage/flat discount, validity window, usage limits) applied at order-calculation time.

### Cancellations, Waitlist & Wishlist
- Tiered cancellation/refund policy per event, configurable by the organizer, with a `/cancellations/preview/{bookingId}` endpoint so a user sees the refund amount before confirming.
- A cancellation frees seats and can trigger waitlist notifications to the next eligible waiter for that event (`WaitlistService.notifyNextWaiter`).
- Wishlist entries can be flagged to notify the user if the event becomes available again.
- A signed Razorpay refund webhook endpoint (`POST /cancellations/webhook/refund`) is publicly reachable (webhook-only, no user session) but signature-gated.

### Tickets & Documents
- QR-code tickets: a per-booking JWT (HMAC-SHA256, ZXing-rendered) is embedded in the ticket; `POST /organizer/checkin` verifies the token, checks payment/cancellation/already-scanned state, and marks attendance.
- PDF tickets and invoices are generated with PDFBox (hand-rolled layout, embedded fonts) and stored in S3 under `tickets/`, `cancellations/`, and `events/images/` prefixes — each with its own IAM-scoped read/write policy and its own S3 lifecycle rule.
- Post-booking documents (PDF + emails + optional SMS) are generated asynchronously so the payment-verification HTTP response isn't blocked on them.

### Reviews
- One review per user per event, restricted to users with a verified booking for that event.

### Organizer Tools
- Self-service registration behind an OTP-gated signup flow, live only after admin approval; approval/rejection status is enforced centrally by `AccessControl.isApprovedOrganizer`, not re-implemented per controller.
- Own-event CRUD (draft → submit → moderation), per-event attendee list, seat-category configuration, revenue breakdown, payout requests.

### Admin Tools
- Event moderation (pending list, approve/reject/revoke), feature/unfeature toggle, platform-wide event stats.
- Organizer approval queue (approve/reject/delete).
- Category CRUD.
- Payout settlement calculator per organizer and payout status lifecycle (request → process/reject).
- Platform revenue report.

### Platform-level hardening
- Bucket4j token-bucket rate limiting (10 requests/60s per client IP) specifically on the six OTP request/verify endpoints — configured in `RateLimitFilter`, ordered ahead of the JWT filter.
- A second, coarser rate-limiting layer at the nginx reverse proxy on every EC2 instance (10 req/s sustained, burst 20) covering *all* traffic, not just auth — added specifically because the in-app Bucket4j limiter is per-instance/in-memory and therefore not consistent across the ASG.
- Centralized, consistent 401/403 JSON error bodies via `SecurityConfig`'s `authenticationEntryPoint`/`accessDeniedHandler`, replacing Spring Security's default empty-body responses.
- Correlation ID filter tags every request for log tracing; structured JSON logging (Logstash encoder) is available via the `json` Spring profile, emitting `correlationId`, `userId`, `traceId`/`spanId` (Micrometer/Brave) alongside each log line.
- Caffeine in-process caching on hot read paths (published/featured/trending events, categories) with per-cache TTLs.

## Auth, briefly

Three JWT tokens instead of one: access (15 min), refresh (7 days), session (30 days), each with its own secret. Refresh/session tokens live in `sessionStorage` per tab instead of cookies, for the multi-tab reason above. Replaying an already-rotated refresh token nukes the whole session — treated as a signal the token was stolen, not just an expired-token edge case.

## Running locally

Requires Java 17 and Maven. MySQL running somewhere (or `docker-compose up`, which also brings up Redis). Redis is optional — `OtpStore` falls back to an in-memory `ConcurrentHashMap` if it can't connect, no extra setup needed for local dev (see [Database & Migrations](#database--migrations) for why this matters beyond local dev).

Set these env vars (or drop them in `.env` — `spring-dotenv` loads it automatically):
```
DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS
JWT_ACCESS_SECRET, JWT_REFRESH_SECRET, JWT_SESSION_SECRET
EMAIL_USER, EMAIL_PASS
RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET
```
Everything else (S3, Twilio, Redis, admin notification email) has a fallback and isn't required for local dev.

```bash
mvn spring-boot:run
```
Serves the API and the static frontend both on `:8080`.

Local dev uses `ddl-auto=update` and Flyway is off, to avoid migration overhead while the schema is still actively changing. Production flips this: `application-prod.properties` turns Flyway on and switches to `ddl-auto=validate`, which is what caught the INT vs BIGINT drift between the early hand-written schema and the JPA entities — see the migration history in `src/main/resources/db/migration`, where V7 through V11 clean this up (widening ID columns to BIGINT, fixing DECIMAL/DOUBLE and ENUM/VARCHAR and TINYINT/CHAR type mismatches between Hibernate's expectations and the actual column types).

## Environment Variables

```env
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=ticket_db
DB_USER=root
DB_PASS=your_password

# JWT — one secret per token type, each min 32 chars
JWT_ACCESS_SECRET=your_access_secret
JWT_REFRESH_SECRET=your_refresh_secret
JWT_SESSION_SECRET=your_session_secret
QR_JWT_SECRET=your_qr_ticket_secret          # signs per-ticket QR JWTs

# Email (Gmail + App Password)
EMAIL_USER=your@gmail.com
EMAIL_PASS=your_app_password
ADMIN_EMAIL=                                 # optional — notified on new organizer applications

# Razorpay
RAZORPAY_KEY_ID=rzp_test_xxx
RAZORPAY_KEY_SECRET=your_razorpay_secret

# AWS S3 (ticket/invoice/event-image storage) — optional locally
AWS_REGION=ap-south-1
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
S3_BUCKET_NAME=

# Redis (OTP store) — optional, falls back to in-memory
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Twilio SMS — optional, all three required together to enable SMS
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_MESSAGING_SERVICE_SID=

# Frontend / CORS
FRONTEND_URL=http://localhost:8080
CORS_ADDITIONAL_ORIGINS=http://localhost:3000,http://localhost:8080,https://localhost:8443
APP_BASE_URL=http://localhost:8080           # used to build deep-links in SMS bodies

# Cookie / HTTPS
COOKIE_SECURE=false                          # true in production
USE_HTTPS=false                              # local dev only w/ mkcert — AWS terminates TLS at the ALB
SSL_KEYSTORE_PATH=classpath:certs/keystore.p12
SSL_KEYSTORE_PASSWORD=changeit

# Business rules (defaults shown, all overridable)
booking.convenience-fee-rate=0.10            # not an env var — see application.properties
booking.gst-rate=0.09
cancellation.fee-rate=0.05
cancellation.fee-gst-rate=0.05
cancellation.high-tier-hours=72

# Scheduler tuning
SEAT_HOLD_SWEEP_MS=60000
EVENT_REMINDER_CRON=0 0 9 * * *
```

> The four business-rule rates and the two scheduler-tuning values above are read from `application.properties`, not directly from the process environment by default — override them by setting `SEAT_HOLD_SWEEP_MS`/`EVENT_REMINDER_CRON` (which *are* `${...}`-templated), or by editing the properties file / supplying a `-D` system property for the fee rates, which are currently hardcoded percentages in `application.properties` rather than individually templated env vars.

## Database & Migrations

Two schema-management paths, matching the dev/prod split above:

- **Local dev (default):** `spring.jpa.hibernate.ddl-auto=update` — Hibernate derives and evolves the schema from the JPA entities directly. Fast to iterate on, but never used in production.
- **Production (`SPRING_PROFILES_ACTIVE=prod`):** Flyway is enabled and Hibernate is switched to `ddl-auto=validate` (fails fast on startup if the DB schema doesn't match the entities, rather than silently mutating it). Flyway applies `src/main/resources/db/migration/V1__baseline_schema.sql` through `V11__fix_tinyint_char_type_mismatch.sql` in order on every boot.

The top-level `db/` folder (`schema.sql`, `organizer_migration.sql`, etc.) is a holdover from the original Node.js/Express version of this project and from before Flyway was introduced — it is **not** read by the Spring Boot app in any environment and is kept only for historical reference. `docker-compose.yml` explicitly no longer mounts it, for the same reason.

To run with prod-parity locally (Flyway-managed schema instead of Hibernate auto-DDL), set `SPRING_PROFILES_ACTIVE=prod` on the backend service.

## API Reference

### Auth (`/auth`)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/signup-request` | Public, rate-limited | Send signup OTP |
| POST | `/auth/signup-verify` | Public, rate-limited | Verify OTP & create account |
| POST | `/auth/login-request` | Public, rate-limited | Send login OTP |
| POST | `/auth/login-verify` | Public, rate-limited | Verify OTP, issue access/refresh/session tokens |
| POST | `/auth/organizer-signup-request` | Public, rate-limited | Send organizer signup OTP |
| POST | `/auth/organizer-signup-verify` | Public, rate-limited | Verify OTP & create organizer account (pending) |
| POST | `/auth/refresh` | Cookie or body | Rotate refresh token, issue new access token |
| POST | `/auth/logout` | Auth | Revoke current session's refresh token |
| POST | `/auth/logout-all` | Auth | Revoke every refresh token for the user |
| GET | `/auth/me` | Auth | Current user id/role |

### User (`/user`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/user/profile` | Auth | Profile |
| PUT | `/user/profile` | Auth | Update profile |
| PUT | `/user/profile/password` | Auth | Change password |
| POST | `/user/avatar` | Auth | Upload avatar |

### Events (`/events`) & Categories
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/events` | Public | All published events |
| GET | `/events/{id}` | Public | Single event |
| GET | `/events/featured` | Public | Featured events (Caffeine-cached) |
| GET | `/events/trending` | Public | Trending events (Caffeine-cached) |
| GET | `/events/pending` | Admin | Events awaiting moderation |
| GET | `/events/admin/stats` | Admin | Platform event stats |
| POST | `/events` | Admin | Create event (auto-published) |
| PUT | `/events/{id}` | Admin | Update event |
| DELETE | `/events/{id}` | Admin | Delete event |
| PUT | `/events/{id}/feature` / `/unfeature` | Admin | Toggle featured flag |
| PUT | `/events/{id}/approve` / `/reject` / `/revoke` | Admin | Moderation actions |
| GET | `/categories` | Public | Active categories (Caffeine-cached) |
| GET/POST/PUT/DELETE | `/admin/categories(/{id})` | Admin | Category CRUD |

### Search (`/search`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/search` | Public | Global search |
| GET | `/search/events` | Public | Filtered event search |
| GET | `/search/cities` | Public | Distinct cities |

### Seats (`/seats`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/seats/{eventId}` | Auth | Seat map |
| POST | `/seats/{eventId}/hold` | Auth | Hold selected seats |
| POST | `/seats/{eventId}/configure` | Organizer/Admin | Configure seat categories/pricing |

### Bookings (`/bookings`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/bookings/my-bookings` | Auth | Current user's bookings |
| GET | `/bookings/{id}/download-ticket` | Auth | Download PDF ticket |
| GET | `/bookings/{id}/download-invoice` | Auth | Download PDF invoice |

### Payments (`/payments`)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/payments/create-order` | Auth | Create Razorpay order |
| POST | `/payments/verify` | Auth | Verify signature, confirm booking, trigger async PDF/email/SMS |

### Coupons (`/coupons`)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/coupons/validate` | Public | Validate a code against an order amount |
| POST | `/coupons` | Admin | Create coupon |
| GET | `/coupons` | Admin | List coupons |
| PATCH | `/coupons/{id}/status` | Admin | Activate/deactivate |

### Reviews (`/reviews`)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/reviews/events/{eventId}` | Auth (verified booking) | Submit review |
| GET | `/reviews/events/{eventId}` | Public | List reviews |
| GET | `/reviews/events/{eventId}/summary` | Public | Aggregate rating |

### Wishlist (`/wishlist`) & Waitlist (`/waitlist`)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST/DELETE | `/wishlist/{eventId}` | Auth | Save/remove |
| GET | `/wishlist` | Auth | Current user's wishlist |
| POST/DELETE | `/waitlist/{eventId}` | Auth | Join/leave |
| GET | `/waitlist` | Auth | Current user's waitlist entries |
| GET | `/waitlist/{eventId}/stats` | Public | Queue size |

### Check-in
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/organizer/checkin` | Auth | Verify a ticket's QR JWT, mark attendance |

### Cancellations (`/cancellations`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/cancellations/preview/{bookingId}` | Auth | Preview refund amount |
| POST | `/cancellations/{bookingId}` | Auth | Cancel a booking |
| GET | `/cancellations/{bookingId}/download-invoice` | Auth | Download cancellation credit note |
| GET/PUT | `/cancellations/policy/{eventId}` | Auth / Organizer | Get/set cancellation policy |
| POST | `/cancellations/webhook/refund` | Public (signed webhook) | Razorpay refund webhook |

### Organizer (`/organizer`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET/PUT | `/organizer/profile` | Organizer | Business profile |
| GET | `/organizer/stats` | Organizer | Dashboard stats |
| GET/POST | `/organizer/events` | Organizer | List/create own events |
| POST | `/organizer/events/{id}/submit` | Organizer | Submit for moderation |
| PUT/DELETE | `/organizer/events/{id}` | Organizer | Update/delete own event |
| GET | `/organizer/events/{id}/attendees` | Organizer | Attendee list |
| GET | `/organizer/revenue` | Organizer | Revenue breakdown |
| GET | `/organizer/admin/organizers` | Admin | List organizers |
| PUT | `/organizer/admin/organizers/{id}/approve` / `/reject` | Admin | Approve/reject organizer |
| DELETE | `/organizer/admin/organizers/{id}` | Admin | Delete organizer |

### Payouts (`/payouts`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/payouts/organizer` | Organizer | Own payout history |
| POST | `/payouts/request` | Organizer | Request a payout |
| GET | `/payouts/admin` | Admin | List all payouts |
| GET | `/payouts/admin/settlement/{organizerId}` | Admin | Calculate outstanding settlement |
| PUT | `/payouts/{id}/process` / `/reject` | Admin | Update payout status |

### Revenue (`/api`) & Images (`/api/images`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/revenue` | Admin | Platform revenue report |
| POST | `/api/images/upload` | Auth | Upload an event image (multipart, 5MB limit) |
| GET | `/api/images/**` | Public | Proxy/serve stored images |

### Misc
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/health` | Public | Health check (ALB target group probe) |

## Frontend Pages

Served as static resources baked into the jar (`src/main/resources/static/`), routed through `SecurityConfig`'s public page allow-list: `/`, `/events-page`, `/my-bookings`, `/payment`, `/seat-selection`, `/organizer-register`, `/organizer-dashboard`, `/organizer-events`, `/organizer-revenue`, `/organizer-payout`, `/my-profile`, `/admin`, `/admin/**`, `/admin-revenue`, `/admin-categories`, `/admin-moderation`, `/admin-payouts`. Three distinct visual themes are used across the user, organizer, and admin dashboards.

## Testing

```bash
mvn test
```

The test suite currently consists of a single Spring Boot context smoke test (`TicketAppApplicationSmokeTest`) verifying the application context loads. The GitHub Actions `test` job runs this against a real MySQL 8.0 service container on every push to `main`, every PR targeting `main`, and every manual `workflow_dispatch`.

## Deploying

CI/CD is GitHub Actions → ECR → EC2 ASG behind an ALB, RDS for the database. Uses OIDC so there are zero long-lived AWS keys sitting in GitHub secrets.

**Current pipeline shape** (`.github/workflows/docker-build.yml`):
1. `checks` — fails fast on unresolved merge-conflict markers.
2. `test` — runs on every push to `main`, every PR, and every manual dispatch; spins up a real MySQL 8.0 service container.
3. `deploy` — runs **only** on a manual `workflow_dispatch` (Actions tab → "Run workflow", or `gh workflow run docker-build.yml`). A plain push to `main` — including the very first one, before any AWS infrastructure exists — only ever runs `test`, so it can never fail trying to assume an OIDC role that doesn't exist yet.

There's a chicken-and-egg problem on first setup: the deploy job needs an IAM role that only exists after Terraform runs, but Terraform-first leaves EC2 with nothing to pull from ECR. The current pipeline design resolves this by construction rather than by a manual flag:

1. Manually dispatch the workflow (before any AWS infra exists) → `test` passes, `deploy` runs, builds and pushes the image to ECR. No running EC2 instances are found yet, so the SSM-deploy step logs that clearly and exits cleanly (exit 0) rather than failing.
2. Targeted `terraform apply -target=...` to create just the OIDC provider/role, ECR repo, and SSM parameters.
3. Full `terraform apply` → VPC/RDS/ALB/ASG all come up; the first EC2 instance's `user_data.sh` fetches secrets from SSM and pulls the image already sitting in ECR from step 1.
4. From then on, every manual `workflow_dispatch` rebuilds, re-pushes, and redeploys to every running ASG instance via SSM `RunShellScript`. A plain push to `main` still only runs tests — deploying remains a deliberate, manual action.

Infrastructure-wise: 2 AZs, private subnets with a NAT gateway, RDS MySQL **Multi-AZ with automatic failover** plus a read replica, storage encrypted at rest, S3 versioned + AES-256 encrypted with per-prefix lifecycle rules (tickets, cancellation invoices, event images), CloudWatch alarms wired to SNS, and CPU-based ASG scale-out/scale-in policies. Secrets (DB credentials, JWT secrets, Razorpay keys, email credentials) are written to AWS SSM Parameter Store as `SecureString` by Terraform and fetched by each EC2 instance at boot via `aws ssm get-parameter --with-decryption` — not baked into the launch template or user-data in plaintext. An nginx reverse-proxy layer sits in front of Spring Boot on each instance (listening on the ALB's target port, proxying to Spring Boot on `127.0.0.1` only) for per-IP rate limiting, as a cost-conscious alternative to AWS WAF.

## Known issues / not yet cleaned up

- Dead `showtimeId`/`movieId` columns and methods remain from a cinema/movie vertical that was built and then removed — still present on the `Seat`, `Booking`, and `Review` entities, and `ReviewRepository` still has live `findByMovieId...` query methods that nothing in the current event-ticketing flow calls. Harmless, just unused. Should be dropped at some point.
- `config/RoleCheck.java` is dead code — it was the original home for the admin/organizer role-check logic before that logic was consolidated into `security/AccessControl.java`, and nothing in the codebase references it anymore (`AccessControl`'s own Javadoc says as much). Safe to delete.
- Bucket4j rate limiting is per-instance/in-memory, so it's not actually consistent across the ASG — the nginx layer handles the real cross-instance limiting, and only the nginx layer covers non-auth traffic.
- Redis is only used for OTP right now. The event-listing/category caches are a separate in-process Caffeine cache, so that's also not shared across instances. Fine at the current scale.
- No free-event payment bypass currently exists in the codebase, despite Razorpay's well-known refusal to create a ₹0 order — every booking, including a ₹0-priced event, goes through the same `/payments/create-order` → Razorpay flow today. Booking a genuinely free event will fail at order creation until this is built.
- No license file yet.

## License

Not decided yet — treat as all-rights-reserved for now.
