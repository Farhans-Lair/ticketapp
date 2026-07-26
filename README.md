# TicketVerse

A BookMyShow-style event ticketing platform. Started as an exercise in learning Spring Boot properly (the original version was a Node/Express + Sequelize app), and has since grown well past that — full seat categories, coupons, QR check-in, wishlist/waitlist, refunds, organizer payouts, the works. Backend is Spring Boot 3.2 / Java 17, frontend is plain HTML/JS (no framework — hasn't been needed so far), MySQL for the database, deployed on AWS via Terraform.

## Why Spring Boot

Migrated off Express to get proper hands-on experience with the Java/Spring ecosystem rather than just reading about it. The existing Controller → Service → Model → DB layout in the Express app mapped over cleanly to Controller → Service → Repository → Entity. Getting to parity took a few rounds — there's a fun story about `@PreAuthorize` silently returning empty 403 bodies that cost an afternoon to track down (see `SecurityConfig`) — but at this point Spring Boot does more than the original ever did.

## What's actually in here

- Email OTP auth (signup + login, both user and organizer), three-token JWT setup (access/refresh/session) with rotation and stolen-token detection
- Events: CRUD, categories, city/search filters, featured/trending, admin moderation
- Seats: category tiers with per-seat pricing, pessimistic locking so two people can't book the same seat, a hold timer during checkout
- Razorpay payments (with a free-event bypass since Razorpay won't create a ₹0 order), coupons
- QR ticket generation + organizer check-in scanning
- PDF tickets and invoices (hand-rolled with PDFBox — right-alignment took longer than it should have)
- Cancellations with a tiered refund policy, wishlist/waitlist with notifications on cancellation
- Reviews (locked to verified bookings only)
- Organizer payout/settlement flow, admin revenue dashboard
- Redis-backed OTP storage (falls back to in-memory if Redis is down — an early `catch(RuntimeException)` was accidentally swallowing Redis connection errors before this was cleaned up)
- Rate limiting (Bucket4j + an nginx layer in front on AWS), Caffeine caching, structured logging

## Stack

Spring Boot 3.2, Java 17, Spring Security, JPA/Hibernate + Flyway, Bucket4j, JJWT, PDFBox, ZXing for QR, AWS SDK v2, Razorpay SDK, Twilio, Spring Mail. Frontend is plain HTML/CSS/JS — DM Sans + Playfair Display, dark theme, three separate dashboard themes for user/organizer/admin. MySQL 8 (RDS in prod, with a read replica), Redis for OTP only. Infrastructure is all Terraform — VPC across 2 AZs, ALB, EC2 ASG, RDS, S3, ECR, SSM for secrets, CloudWatch alarms. CI/CD is GitHub Actions with OIDC (no stored AWS keys).

## Running locally

Requires Java 17 and Maven. MySQL running somewhere (or `docker-compose up`). Redis is optional — falls back to in-memory OTP storage if it can't connect, no extra setup needed for local dev.

Set these env vars (or drop them in `.env`):
```
DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS
JWT_ACCESS_SECRET, JWT_REFRESH_SECRET, JWT_SESSION_SECRET
EMAIL_USER, EMAIL_PASS
RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET
```
Everything else (S3, Twilio, Redis) has a fallback and isn't required for local dev.

```bash
mvn spring-boot:run
```
Serves the API and the static frontend both on :8080.

Local dev uses `ddl-auto=update` and Flyway is off, to avoid migration overhead while the schema is still actively changing. Production flips this: `application-prod.properties` turns Flyway on and switches to `ddl-auto=validate`, which is what caught the INT vs BIGINT drift between the early hand-written schema and the JPA entities — see the migration history in `db/migration`, where V7 through V11 clean this up.

## Deploying

CI/CD is GitHub Actions → ECR → EC2 ASG behind an ALB, RDS for the database. Uses OIDC so there are zero long-lived AWS keys sitting in GitHub secrets.

There's a chicken-and-egg problem on first setup: the pipeline needs an IAM role that only exists after Terraform runs, but Terraform-first leaves EC2 with nothing to pull from ECR. Worked around with a `bootstrap` branch — pushing there only runs tests, never touches AWS. Then:

1. Push to `bootstrap` → tests run, nothing AWS-related happens
2. Targeted `terraform apply` to create just the OIDC role + ECR repo
3. Merge to `main` → full pipeline runs, image gets built and pushed
4. Full `terraform apply` → VPC/RDS/ALB/ASG all come up, first EC2 instance already has an image to pull
5. After that, every push to `main` just works

Infrastructure-wise: two AZs, private subnets with NAT gateways, RDS with a read replica, S3 versioned + encrypted, CloudWatch alarms wired to SNS. An nginx reverse-proxy layer sits in front of Spring Boot on each instance for rate limiting, as an alternative to a paid WAF.

## Auth, briefly

Three JWT tokens instead of one: access (15 min), refresh (7 days), session (30 days), each with its own secret. Refresh/session tokens live in `sessionStorage` per tab instead of cookies — cookies are scoped to the browser, not the tab, so two logged-in accounts in two tabs of the same browser were stepping on each other before this was fixed. Replaying an already-rotated refresh token nukes the whole session, treated as a signal the token was stolen.

## Known issues / not yet cleaned up

- Dead `showtimeId` / `movieId` columns remain from a cinema/movie vertical that was built and then removed. Harmless, just unused. Should be dropped at some point.
- Bucket4j rate limiting is per-instance/in-memory, so it's not actually consistent across the ASG — the nginx layer handles the real cross-instance limiting.
- Redis is only used for OTP right now. The event-listing cache is a separate in-process Caffeine cache, so that's also not shared across instances. Fine at the current scale.
- No license file yet.

## License

Not decided yet — treat as all-rights-reserved for now.
