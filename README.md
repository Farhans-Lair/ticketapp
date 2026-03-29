# Ticket Booking Application — Spring Boot Backend

Migrated from **Express.js** to **Spring Boot 3.2 (Java 17)**.  
All API endpoints, request shapes, response shapes, and frontend behaviour are **identical** to the original.

---

## Project Structure

```
ticketapp/
├── src/
│   └── main/
│       ├── java/com/ticketapp/
│       │   ├── TicketAppApplication.java       # Entry point (@SpringBootApplication)
│       │   ├── config/
│       │   │   ├── SecurityConfig.java         # Spring Security, JWT filter, CORS
│       │   │   ├── WebConfig.java              # Static files + SPA HTML routing
│       │   │   ├── S3Config.java               # AWS SDK v2 S3Client bean
│       │   │   └── RoleCheck.java              # @PreAuthorize helper bean
│       │   ├── entity/                         # JPA @Entity classes (= Sequelize models)
│       │   │   ├── User.java
│       │   │   ├── OrganizerProfile.java
│       │   │   ├── Event.java
│       │   │   ├── Seat.java
│       │   │   └── Booking.java
│       │   ├── repository/                     # Spring Data JPA interfaces (= DB queries)
│       │   │   ├── UserRepository.java
│       │   │   ├── OrganizerProfileRepository.java
│       │   │   ├── EventRepository.java
│       │   │   ├── SeatRepository.java
│       │   │   └── BookingRepository.java
│       │   ├── dto/                            # Request/Response body classes
│       │   │   ├── AuthDto.java
│       │   │   ├── EventDto.java
│       │   │   ├── PaymentDto.java
│       │   │   └── OrganizerProfileDto.java
│       │   ├── security/
│       │   │   ├── JwtUtil.java                # Token generation & validation (jjwt)
│       │   │   ├── JwtAuthFilter.java          # Replaces auth.middleware.js
│       │   │   └── AuthenticatedUser.java      # Holds parsed JWT claims (= req.user)
│       │   ├── service/                        # Business logic (= /services/*.js)
│       │   │   ├── OtpStore.java               # In-memory OTP Map + TTL sweep
│       │   │   ├── EmailService.java           # JavaMailSender (= nodemailer)
│       │   │   ├── AuthService.java            # Signup / Login / Organizer signup flows
│       │   │   ├── EventService.java           # Event CRUD + seat generation
│       │   │   ├── SeatService.java            # Seat fetch + atomic booking
│       │   │   ├── BookingService.java         # Amount calculation + booking confirmation
│       │   │   ├── PaymentService.java         # Razorpay order + HMAC signature verify
│       │   │   ├── OrganizerService.java       # Profile, revenue, stats, admin mgmt
│       │   │   ├── PdfService.java             # Ticket PDF (Apache PDFBox = pdfkit)
│       │   │   └── S3Service.java              # Upload/fetch ticket PDFs (AWS SDK v2)
│       │   ├── controller/                     # @RestController (= routes + controllers)
│       │   │   ├── HealthController.java       # GET /health
│       │   │   ├── AuthController.java         # /auth/*
│       │   │   ├── EventController.java        # /events/*
│       │   │   ├── BookingController.java      # /bookings/*
│       │   │   ├── PaymentController.java      # /payments/*
│       │   │   ├── SeatController.java         # /seats/*
│       │   │   ├── RevenueController.java      # /api/revenue
│       │   │   └── OrganizerController.java   # /organizer/*
│       │   └── exception/
│       │       └── GlobalExceptionHandler.java # Mirrors error.middleware.js
│       └── resources/
│           ├── application.properties          # All config (= .env variables)
│           └── static/                         # Frontend served by Spring Boot
│               ├── index.html
│               ├── events.html
│               ├── ... (all HTML pages)
│               ├── js/
│               │   ├── api.js                  # ✅ Updated for Spring Boot error format
│               │   └── ... (all other JS files unchanged)
│               └── css/
├── db/
│   ├── schema.sql                              # Unchanged — same MySQL schema
│   └── organizer_migration.sql
├── Dockerfile                                  # Multi-stage Maven → JRE build
├── docker-compose.yml                          # Spring Boot + MySQL
├── .env.example                                # All environment variables
└── pom.xml                                     # Maven dependencies
```

---

## API Routes (100% unchanged from Express)

| Method | Path | Auth | Role |
|--------|------|------|------|
| POST | `/auth/signup-request` | Public | — |
| POST | `/auth/signup-verify` | Public | — |
| POST | `/auth/login-request` | Public | — |
| POST | `/auth/login-verify` | Public | — |
| POST | `/auth/organizer-signup-request` | Public | — |
| POST | `/auth/organizer-signup-verify` | Public | — |
| POST | `/auth/logout` | ✅ | any |
| GET | `/auth/me` | ✅ | any |
| GET | `/events` | ✅ | any |
| POST | `/events` | ✅ | admin |
| PUT | `/events/:id` | ✅ | admin |
| DELETE | `/events/:id` | ✅ | admin |
| GET | `/bookings/my-bookings` | ✅ | any |
| GET | `/bookings/:id/download-ticket` | ✅ | any |
| POST | `/payments/create-order` | ✅ | any |
| POST | `/payments/verify` | ✅ | any |
| GET | `/seats/:eventId` | ✅ | any |
| GET | `/api/revenue` | ✅ | admin |
| GET | `/organizer/profile` | ✅ | organizer |
| PUT | `/organizer/profile` | ✅ | organizer |
| GET | `/organizer/stats` | ✅ | organizer |
| GET | `/organizer/events` | ✅ | organizer |
| POST | `/organizer/events` | ✅ | organizer |
| PUT | `/organizer/events/:id` | ✅ | organizer |
| DELETE | `/organizer/events/:id` | ✅ | organizer |
| GET | `/organizer/events/:id/attendees` | ✅ | organizer |
| GET | `/organizer/revenue` | ✅ | organizer |
| GET | `/organizer/admin/organizers` | ✅ | admin |
| PUT | `/organizer/admin/organizers/:id/approve` | ✅ | admin |
| PUT | `/organizer/admin/organizers/:id/reject` | ✅ | admin |
| DELETE | `/organizer/admin/organizers/:id` | ✅ | admin |

---

## About Dependencies

**The JAR files are NOT included in the zip — this is correct and intentional.**

`pom.xml` is the Java equivalent of `package.json`. It declares all libraries,
and Maven downloads them automatically from Maven Central on the first build —
exactly like `npm install` fetches from npmjs.com.

Dependencies downloaded on first build (~50 MB, cached permanently after that):
- Spring Boot 3.2 (web, security, data JPA, mail, validation)
- jjwt (JWT generation/verification)
- Apache PDFBox (ticket PDF generation)
- razorpay-java SDK
- AWS SDK v2 for S3
- MySQL JDBC driver
- Lombok

---

## Running Locally

### Option A — Docker Compose (recommended — no Java or Maven install needed)

Docker handles Java 17 and Maven inside the build container automatically.
The only prerequisite is **Docker Desktop**.

```bash
# 1. Copy and configure environment variables
cp .env.example .env
# Edit .env with your DB, email, Razorpay, and S3 credentials

# 2. Build and start (downloads dependencies inside container on first run)
docker compose up --build

# App is live at http://localhost:3000
```

### Option B — Run directly (Java 17 required; Maven is auto-downloaded)

A **Maven Wrapper** (`mvnw` / `mvnw.cmd`) is included. It downloads Maven 3.9.6
automatically — so the only hard requirement is **Java 17 JDK**.

Install Java 17: https://adoptium.net (free, open source Temurin build)

```bash
# 1. Create the MySQL database
mysql -u root -p < db/schema.sql
mysql -u root -p ticketdb < db/organizer_migration.sql

# 2. Configure environment variables
cp .env.example .env
# Edit .env with your credentials

# 3. Build (Maven Wrapper downloads Maven + all dependencies automatically)
#    First build takes ~2 min as dependencies download; subsequent builds are fast.

# Linux / Mac:
chmod +x mvnw
./mvnw package -DskipTests

# Windows:
mvnw.cmd package -DskipTests

# 4. Run
java -jar target/ticket-booking-backend-1.0.0.jar

# App is live at http://localhost:3000
```

### Option C — If you already have Maven 3.9+ installed

```bash
mvn package -DskipTests
java -jar target/ticket-booking-backend-1.0.0.jar
```

---

---

## Key Migration Decisions

| Express.js | Spring Boot | Notes |
|---|---|---|
| `dotenv` | `application.properties` + env vars | Spring reads `${ENV_VAR}` natively |
| Sequelize ORM | Spring Data JPA + Hibernate | Same MySQL schema, no changes needed |
| `jsonwebtoken` | `jjwt` (io.jsonwebtoken) | Same HMAC-SHA256, same token structure |
| `bcrypt` | `BCryptPasswordEncoder` | Same bcrypt algorithm, cost factor 10 |
| In-memory OTP Map | `ConcurrentHashMap` + `@Scheduled` sweep | Thread-safe; production should use Redis |
| `nodemailer` | `JavaMailSender` | Same Gmail SMTP config |
| `pdfkit` | Apache PDFBox | Same ticket layout |
| `razorpay` SDK | `razorpay-java` | Same HMAC signature verification |
| `@aws-sdk/client-s3` | AWS SDK v2 for Java | Same S3 operations |
| `express-rate-limit` | _(add Bucket4j if needed)_ | Not included by default |
| `express-validator` | Jakarta Bean Validation `@Valid` | Same field rules |

## Production Checklist

- [ ] Set `JWT_SECRET` to a random 64-char string
- [ ] Set `COOKIE_SECURE=true` (HTTPS only)
- [ ] Set `spring.jpa.hibernate.ddl-auto=validate` in `application.properties` (already set)
- [ ] Add Bucket4j rate limiting if exposing auth endpoints publicly
- [ ] Switch OTP store to Redis for multi-instance deployments
- [ ] Enable Spring Boot Actuator for health/metrics endpoints
