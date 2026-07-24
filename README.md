# TicketVerse — Event Ticketing Platform

A full-stack, BookMyShow-style event ticketing platform. Spring Boot 3.2 (Java 17) backend, vanilla HTML/JS frontend, MySQL database, and AWS infrastructure managed with Terraform. Originally migrated from an Express.js/Sequelize backend, the project has since grown far beyond parity with the original — seat categories, coupons, QR check-in, wishlists/waitlists, cancellations/refunds, organizer payouts, and a hardened AWS deployment pipeline.

---

## 1. Core Features

| Area | Capability |
|---|---|
| **Auth** | Email OTP signup/login (user + organizer), JWT access/refresh/session token rotation, stolen-token reuse detection, multi-tab session isolation |
| **Events** | CRUD, dynamic categories, city/search filters, featured & trending, admin moderation (approve/reject/revoke) |
| **Seating** | Tiered seat categories with per-seat pricing, pessimistic-lock booking, checkout seat-hold timer with scheduled release |
| **Booking & Payments** | Razorpay order creation + HMAC signature verification, free-event bypass, coupon/discount engine |
| **Tickets** | QR-coded tickets (HMAC-signed), organizer check-in scanning, PDF ticket + invoice generation (Apache PDFBox) |
| **Post-booking** | Async ticket/invoice PDF generation and S3 upload, confirmation email + SMS |
| **Cancellations** | Tiered refund policy engine, refund webhook handling, cancellation invoice PDF |
| **Wishlist / Waitlist** | Save-for-later with availability notification; FIFO waitlist notified on cancellation |
| **Reviews** | Verified-booking-gated ratings & reviews, cached average rating on events |
| **Organizer tools** | Profile & bank/payout details, revenue dashboard, attendee list, payout requests, settlement calculation |
| **Admin tools** | Organizer approval, event moderation, category management, payouts, platform revenue |
| **Notifications** | Gmail SMTP email (OTP, tickets, invoices, reminders, payouts, moderation) + Twilio SMS with carrier-block retry |
| **Platform hardening** | Redis-backed OTP with in-memory fallback, Bucket4j rate limiting, Spring Caffeine caching, optimistic locking on events, structured/correlation-ID logging |
| **Scheduled jobs** | Seat-hold sweep, daily event reminder emails, expired refresh-token cleanup |

---

## 2. Tech Stack

**Backend:** Spring Boot 3.2, Java 17, Spring Security 6, Spring Data JPA / Hibernate, Flyway, Spring Cache (Caffeine), Spring Async/Scheduling, Lombok, Bucket4j, JJWT, PDFBox, ZXing (QR), AWS SDK v2 (S3), Razorpay Java SDK, Twilio SDK, Spring Mail.

**Frontend:** Vanilla HTML5/CSS/JavaScript (no framework/build step), DM Sans + Playfair Display, three themed dashboards (user/organizer/admin).

**Database:** MySQL 8 (RDS in production), 11 Flyway-versioned migrations (`V1`–`V11`). Redis for OTP storage only.

**Infrastructure:** AWS (VPC with public + private subnets across 2 AZs, dual NAT Gateways, ALB, EC2 Auto Scaling Group, RDS MySQL primary + read replica, S3, ECR, SSM Parameter Store, CloudWatch alarms/dashboard, SNS alerting), Terraform, Docker, GitHub Actions (OIDC keyless deploy).

**Build:** Apache Maven 3.9.x.

---

## 3. Project Structure

```
ticketapp/
├── src/main/java/com/ticketapp/
│   ├── TicketAppApplication.java        # Entry point (@EnableScheduling, @EnableAsync)
│   ├── config/                          # Security, CORS, caching, rate limiting, HTTPS, S3, Jackson
│   ├── controller/                      # 21 @RestController classes — one per domain
│   ├── dto/                             # Request/response payload classes
│   ├── entity/                          # 15 JPA entities
│   ├── exception/                       # Business exception hierarchy + global handler
│   ├── repository/                      # Spring Data JPA repositories
│   ├── scheduler/                       # Seat-hold sweep, reminders, token cleanup
│   ├── security/                        # JWT filter, JwtUtil, AuthenticatedUser principal
│   └── service/                         # Business logic layer
├── src/main/resources/
│   ├── application.properties           # Base config (dev defaults)
│   ├── application-prod.properties      # Production overrides (Flyway validate, pooling, logging)
│   ├── db/migration/                    # Flyway V1–V11 SQL migrations
│   └── static/                          # Frontend: HTML pages, css/, js/
├── db/                                  # Legacy/reference SQL scripts (pre-Flyway)
├── terraform/                           # Full AWS infrastructure as code
├── .github/workflows/docker-build.yml   # CI/CD: test → build → push → SSM deploy
├── Dockerfile                           # Multi-stage Maven → JRE build
├── docker-compose.yml                   # Local Spring Boot + MySQL
└── pom.xml
```

---

## 4. API Overview

All endpoints are grouped by domain controller. Public (unauthenticated) routes are marked **Public**; the rest require a valid JWT access token, with several also role-gated (`admin`, `organizer`).

| Domain | Base path | Examples |
|---|---|---|
| Auth | `/auth` | signup/login OTP request+verify, `/refresh`, `/logout`, `/logout-all`, `/me` |
| Events | `/events` | list/search/get, `/featured`, `/trending`, admin moderation (`approve`/`reject`/`revoke`/`feature`), CRUD |
| Event Categories | `/categories`, `/admin/categories` | public list, admin CRUD |
| Seats | `/seats/{eventId}` | list, `/hold`, `/configure` (organizer tiers) |
| Bookings | `/bookings` | `/my-bookings`, `/{id}/download-ticket`, `/{id}/download-invoice` |
| Payments | `/payments` | `/create-order`, `/verify` |
| Coupons | `/coupons` | `/validate`, admin CRUD |
| Cancellations | `/cancellations` | preview, cancel, invoice download, policy CRUD, refund webhook |
| Check-in | `/organizer/checkin` | QR scan validation |
| Reviews | `/reviews` | submit, list, rating summary |
| Wishlist | `/wishlist` | save/remove/list |
| Waitlist | `/waitlist` | join/leave/list/stats |
| Search | `/search` | global search, filtered events, cities |
| User | `/user` | profile, password change, avatar upload |
| Organizer | `/organizer` | profile, stats, events, attendees, revenue, admin organizer management |
| Payouts | `/payouts` | organizer request/list, admin list/settlement/process/reject |
| Revenue | `/api/revenue` | admin platform revenue |
| Images | `/api/images` | upload + serve (S3 with local-disk fallback) |
| Health | `/health` | liveness check |

---

## 5. Getting Started (Local Development)

### Prerequisites
- Java 17, Apache Maven 3.9+
- MySQL 8 (or `docker-compose up` for MySQL + backend together)
- (Optional) Redis — falls back to in-memory OTP storage if unreachable

### Configuration
Copy `.env.example` → `.env` (or export variables directly) with at minimum:
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`, `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`, `JWT_SESSION_SECRET`, `EMAIL_USER`, `EMAIL_PASS`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`. AWS S3, Twilio, and Redis variables are optional in dev (each has a safe fallback).

### Run
```bash
mvn spring-boot:run
# or
docker-compose up --build
```
The app serves both the API and the static frontend on `http://localhost:8080`.

### Database Migrations
Local dev uses `spring.jpa.hibernate.ddl-auto=update` (`spring.flyway.enabled=false`). Production uses the `prod` Spring profile, which enables Flyway (`db/migration/V1…V11`) and switches Hibernate to `ddl-auto=validate` — the schema must exactly match JPA entity types (Flyway owns all DDL in production).

---

## 6. Deployment (AWS)

CI/CD runs via GitHub Actions using OIDC keyless authentication (no long-lived AWS secrets in GitHub). A `bootstrap` branch pattern solves the chicken-and-egg problem of the OIDC role and ECR repository not existing on first deploy:

1. Push to `bootstrap` → `test` job only (Maven build + tests against an ephemeral MySQL service container); no AWS calls.
2. Run a targeted `terraform apply` to create only the OIDC provider/role and the ECR repository.
3. Merge/push to `main` → `test` then `deploy` jobs run; the image is built and pushed to ECR.
4. Run the full `terraform apply` to stand up the VPC, RDS, ALB, and Auto Scaling Group.
5. From then on, every push to `main` runs test → build → push to ECR → deploy to the running EC2 instance(s) via SSM.

Infrastructure highlights: VPC across two AZs with public + private subnets and dual NAT Gateways, ALB with health checks, EC2 Auto Scaling Group (min/desired 2) pulling secrets from SSM Parameter Store, RDS MySQL with a read replica and Multi-AZ, encrypted/versioned S3 bucket, and CloudWatch alarms (CPU, credit balance, 5xx rate, unhealthy hosts, RDS storage/connections) wired to an SNS alert topic.

---

## 7. Notable Engineering Decisions

- **Three-token JWT model** — short-lived access token (15 min), rotating refresh token (7 days), and a long-lived session token (30 days), each with an independent secret in SSM. Refresh/session tokens are kept in `sessionStorage` (not cookies) so multiple tabs in the same browser can hold independent sessions; replaying a revoked refresh token revokes the entire session.
- **Schema-first discipline in production** — `ddl-auto=validate` is only active behind the `prod` Spring profile; all schema changes flow through versioned Flyway migrations that must match JPA entity types (`Long` ⇄ `BIGINT`) exactly.
- **Resilient OTP storage** — Redis-backed with an isolated try/catch around the Redis call so infrastructure failures fall back to an in-memory store instead of blocking authentication.
- **Concurrency-safe seat booking** — pessimistic row locking plus a conditional `UPDATE ... WHERE status = 'available'` with row-count verification prevents double-booking under concurrent checkout.
- **Async post-booking pipeline** — ticket/invoice PDF generation and S3 upload run asynchronously after payment confirmation so the booking API response isn't blocked on PDF rendering or mail delivery.

---

## 8. License

Internal/educational project — no license file currently present.
