Ticket Booking Application — Spring Boot Backend
Migrated from Express.js to Spring Boot 3.2 (Java 17).  
All API endpoints, request shapes, response shapes, and frontend behaviour are identical to the original.
---
Project Structure
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
API Routes (100% unchanged from Express)
Method	Path	Auth	Role
POST	`/auth/signup-request`	Public	—
POST	`/auth/signup-verify`	Public	—
POST	`/auth/login-request`	Public	—
POST	`/auth/login-verify`	Public	—
POST	`/auth/organizer-signup-request`	Public	—
POST	`/auth/organizer-signup-verify`	Public	—
POST	`/auth/logout`	✅	any
GET	`/auth/me`	✅	any
GET	`/events`	✅	any
POST	`/events`	✅	admin
PUT	`/events/:id`	✅	admin
DELETE	`/events/:id`	✅	admin
GET	`/bookings/my-bookings`	✅	any
GET	`/bookings/:id/download-ticket`	✅	any
POST	`/payments/create-order`	✅	any
POST	`/payments/verify`	✅	any
GET	`/seats/:eventId`	✅	any
GET	`/api/revenue`	✅	admin
GET	`/organizer/profile`	✅	organizer
PUT	`/organizer/profile`	✅	organizer
GET	`/organizer/stats`	✅	organizer
GET	`/organizer/events`	✅	organizer
POST	`/organizer/events`	✅	organizer
PUT	`/organizer/events/:id`	✅	organizer
DELETE	`/organizer/events/:id`	✅	organizer
GET	`/organizer/events/:id/attendees`	✅	organizer
GET	`/organizer/revenue`	✅	organizer
GET	`/organizer/admin/organizers`	✅	admin
PUT	`/organizer/admin/organizers/:id/approve`	✅	admin
PUT	`/organizer/admin/organizers/:id/reject`	✅	admin
DELETE	`/organizer/admin/organizers/:id`	✅	admin
---
About Dependencies
The JAR files are NOT included in the zip — this is correct and intentional.
`pom.xml` is the Java equivalent of `package.json`. It declares all libraries,
and Maven downloads them automatically from Maven Central on the first build —
exactly like `npm install` fetches from npmjs.com.
Dependencies downloaded on first build (~50 MB, cached permanently after that):
Spring Boot 3.2 (web, security, data JPA, mail, validation)
jjwt (JWT generation/verification)
Apache PDFBox (ticket PDF generation)
razorpay-java SDK
AWS SDK v2 for S3
MySQL JDBC driver
Lombok
---
Running Locally
Option A — Docker Compose (recommended — no Java or Maven install needed)
Docker handles Java 17 and Maven inside the build container automatically.
The only prerequisite is Docker Desktop.
```bash
# 1. Copy and configure environment variables
cp .env.example .env
# Edit .env with your DB, email, Razorpay, and S3 credentials

# 2. Build and start (downloads dependencies inside container on first run)
docker compose up --build

# App is live at http://localhost:3000
```
Option B — Run directly (Java 17 required; Maven is auto-downloaded)
A Maven Wrapper (`mvnw` / `mvnw.cmd`) is included. It downloads Maven 3.9.6
automatically — so the only hard requirement is Java 17 JDK.
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
Option C — If you already have Maven 3.9+ installed
```bash
mvn package -DskipTests
java -jar target/ticket-booking-backend-1.0.0.jar
```
---
---
Key Migration Decisions
Express.js	Spring Boot	Notes
`dotenv`	`application.properties` + env vars	Spring reads `${ENV_VAR}` natively
Sequelize ORM	Spring Data JPA + Hibernate	Same MySQL schema, no changes needed
`jsonwebtoken`	`jjwt` (io.jsonwebtoken)	Same HMAC-SHA256, same token structure
`bcrypt`	`BCryptPasswordEncoder`	Same bcrypt algorithm, cost factor 10
In-memory OTP Map	`ConcurrentHashMap` + `@Scheduled` sweep	Thread-safe; production should use Redis
`nodemailer`	`JavaMailSender`	Same Gmail SMTP config
`pdfkit`	Apache PDFBox	Same ticket layout
`razorpay` SDK	`razorpay-java`	Same HMAC signature verification
`@aws-sdk/client-s3`	AWS SDK v2 for Java	Same S3 operations
`express-rate-limit`	Bucket4j `RateLimitFilter`	10 req/min per IP on all /auth/* endpoints
`express-validator`	Jakarta Bean Validation `@Valid`	Same field rules
Plain-text logs	logstash-logback-encoder + CorrelationFilter	Structured JSON in "json" profile; plain-text in dev
Production Checklist
[ ] Set `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`, and `JWT_SESSION_SECRET` — each a random 64-char string, all DIFFERENT from one another (`openssl rand -base64 48`)
[ ] Set `COOKIE_SECURE=true` (HTTPS only)
[ ] Set `spring.jpa.hibernate.ddl-auto=validate` in `application.properties` (already set)
[x] Add Bucket4j rate limiting if exposing auth endpoints publicly — done (`RateLimitFilter.java`, 10 req/min/IP on `/auth/*`)
[ ] Switch OTP store to Redis for multi-instance deployments
[ ] Enable Spring Boot Actuator for health/metrics endpoints
---
New features added
1. Structured JSON logging
Files: `src/main/resources/logback-spring.xml`, `src/main/java/com/ticketapp/config/CorrelationFilter.java`
Activate with `SPRING_PROFILES_ACTIVE=json` in `.env` (set this on EC2 / in production).
Every log line becomes a JSON object with:
`timestamp`, `level`, `logger`, `message`, `correlationId`, `userId`, `method`, `path`, `traceId`, `spanId`
In local dev (profile not set): unchanged plain-text console output — no developer experience change.
CloudWatch Logs Insights query example:
```
fields @timestamp, level, message, correlationId, userId, path
| filter level = "ERROR"
| sort @timestamp desc
| limit 50
```
2. API rate limiting (Bucket4j)
File: `src/main/java/com/ticketapp/config/RateLimitFilter.java`
Applies to all `/auth/*` endpoints (OTP request + verify flows).
Limit: 10 requests per 60 seconds per client IP (token bucket).
Returns HTTP `429 Too Many Requests` with a `Retry-After` header on breach.
Works transparently behind the ALB — uses `X-Forwarded-For` for real client IP.
To scale to multiple instances: replace the `ConcurrentHashMap` in `RateLimitFilter` with a Bucket4j + Redisson (ElastiCache/Redis) backend. The filter logic stays identical.
3. Resource tagging strategy (Terraform)
File: `terraform/tags.tf`
Every AWS resource now carries: `project`, `env`, `owner`, `cost_centre`, `managed_by`, `repo`.
Set values in `terraform.tfvars`:
```hcl
environment  = "prod"   # prod | staging | dev
owner        = "backend-team"
cost_centre  = "eng-backend"
```
Activate cost allocation in AWS: Billing → Cost allocation tags → activate `project`, `env`, `cost_centre`.
All existing resources use `merge(local.common_tags, { Name = "..." })` — the `Name` tag is preserved exactly as before.