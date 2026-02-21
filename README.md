# e-Arzuhal – Main Server

Main backend server for the e-Arzuhal platform.

This Spring Boot application acts as the central orchestrator for the e-Arzuhal system.
It exposes REST APIs for mobile and web clients, coordinates calls to external services
(NLP, GraphRAG, statistics), manages contract and petition workflows, generates PDF documents,
and handles identity verification for digital signatures.

---

## System Role

The Main Server is responsible for:

- Receiving requests from Mobile and Web clients
- Acting as a lightweight API gateway / orchestrator
- Calling external services:
  - NLP service (port 8001)
  - GraphRAG / Knowledge Graph service (port 8000)
  - Statistics service (port 8002)
- Merging results from multiple services
- Managing:
  - Users
  - Contracts (sözleşmeler) — full CRUD + PDF export
  - Petitions / Dilekçeler — full CRUD + PDF export
  - Approval and digital signature flows
  - **Identity Verification** — NFC / MRZ / manual TC Kimlik doğrulama
- Generating PDF documents from Thymeleaf HTML templates (openhtmltopdf)
- Applying authentication and authorization (JWT / Spring Security)

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.2 |
| Build | Maven |
| Database | PostgreSQL |
| Security | Spring Security 6 + JWT (JJWT 0.12.6) |
| Persistence | Spring Data JPA + Hibernate |
| PDF Generation | openhtmltopdf-pdfbox 1.0.10 |
| Template Engine | Thymeleaf |
| HTTP Client | Spring WebFlux WebClient |
| Logging | SLF4J + Logback (built-in) |

---

## Main Dependencies

```xml
spring-boot-starter-web           <!-- REST API -->
spring-boot-starter-webflux       <!-- WebClient for microservices -->
spring-boot-starter-security      <!-- Authentication -->
spring-boot-starter-data-jpa      <!-- Persistence -->
spring-boot-starter-validation    <!-- Request validation -->
spring-boot-starter-thymeleaf     <!-- HTML template engine -->
jjwt-api / jjwt-impl / jjwt-jackson 0.12.6   <!-- JWT -->
openhtmltopdf-pdfbox 1.0.10       <!-- HTML → PDF -->
openhtmltopdf-slf4j 1.0.10        <!-- PDF library logging -->
postgresql                        <!-- DB driver -->
lombok                            <!-- Boilerplate reduction -->
```

---

## Project Structure

```
src/main/java/com/earzuhal/
├── config/
│   ├── SecurityConfig.java
│   ├── JwtConfig.java
│   └── WebConfig.java
├── security/
│   ├── jwt/
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   └── JwtAuthenticationEntryPoint.java
│   └── CustomUserDetailsService.java
├── Controller/
│   ├── AuthController.java          # /api/auth/**
│   ├── ContractController.java      # /api/contracts/**
│   ├── PetitionController.java      # /api/petitions/**
│   ├── UserController.java          # /api/users/**
│   ├── AnalysisController.java      # /api/analysis/**
│   └── VerificationController.java  # /api/verification/**
├── Service/
│   ├── AuthService.java
│   ├── UserService.java
│   ├── ContractService.java
│   ├── PetitionService.java
│   ├── PdfService.java              # HTML → PDF (Thymeleaf + openhtmltopdf)
│   ├── AnalysisService.java
│   ├── NlpService.java
│   ├── GraphRagService.java
│   └── VerificationService.java     # TC Kimlik doğrulama (checksum + maskeleme)
├── Repository/
│   ├── UserRepository.java
│   ├── ContractRepository.java
│   ├── PetitionRepository.java
│   └── IdentityVerificationRepository.java
├── Model/
│   ├── User.java
│   ├── Contract.java
│   ├── Petition.java
│   └── IdentityVerification.java    # identity_verifications tablosu
├── dto/
│   ├── auth/
│   ├── user/
│   ├── contract/
│   ├── petition/
│   ├── analysis/
│   └── verification/
│       ├── VerificationRequest.java
│       └── VerificationResponse.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── (custom exceptions)
└── MainServerApp.java

src/main/resources/
├── application.properties
├── fonts/                           # Türkçe karakter desteği (opsiyonel, prod için)
│   ├── NotoSerif-Regular.ttf
│   └── NotoSerif-Bold.ttf
└── templates/pdf/
    ├── contracts/
    │   ├── kira_sozlesmesi.html
    │   ├── borc_sozlesmesi.html
    │   ├── hizmet_sozlesmesi.html
    │   ├── satis_sozlesmesi.html
    │   ├── is_sozlesmesi.html
    │   ├── vekaletname.html
    │   ├── taahhutname.html
    │   └── genel_sozlesme.html      # fallback template
    └── petitions/
        └── dilekce.html
```

---

## How to Run

### Prerequisites

- Java 21
- PostgreSQL running locally
- Maven 3.9+

---

### 1. Configure Database

```sql
CREATE DATABASE earzuhal_main_server;
```

---

### 2. Set Environment Variables

**Windows PowerShell:**
```powershell
$env:POSTGRES_DB_PASSWORD="your_postgres_password"
$env:JWT_SECRET="your-very-long-secret-key-at-least-512-bits-for-hs512-algorithm"
```

**Windows CMD:**
```cmd
set POSTGRES_DB_PASSWORD=your_postgres_password
set JWT_SECRET=your-very-long-secret-key-at-least-512-bits-for-hs512-algorithm
```

**Linux/Mac:**
```bash
export POSTGRES_DB_PASSWORD="your_postgres_password"
export JWT_SECRET="your-very-long-secret-key-at-least-512-bits-for-hs512-algorithm"
```

⚠️ **NEVER commit JWT_SECRET to version control!**

---

### 3. Font Setup (Turkish Character Support in PDFs)

PDF generation requires a font with Turkish character support (İ, ı, Ğ, ğ, Ş, ş).

**Development (Windows):** Times New Roman is used automatically from `C:\Windows\Fonts\`.

**Production (Linux/Docker):** Add Noto Serif to the classpath:

1. Download from https://fonts.google.com/specimen/Noto+Serif
2. Place `NotoSerif-Regular.ttf` and `NotoSerif-Bold.ttf` in:
   ```
   src/main/resources/fonts/
   ```
3. The fonts are picked up automatically by `PdfService`.

---

### 4. Build and Run

```bash
cd main-server
./mvnw clean install
./mvnw spring-boot:run
```

Or build and run JAR:

```bash
./mvnw clean package
java -jar target/main_server-0.0.1-SNAPSHOT.jar
```

Application runs on: `http://localhost:8080`

---

## API Endpoints

### Public Endpoints (No Auth)

```
POST /api/auth/register   Register new user
POST /api/auth/login      Login → returns JWT token
```

### User Endpoints (JWT required)

```
GET  /api/users/me        Get current user profile
PUT  /api/users/me        Update profile
```

### Contract Endpoints (JWT required)

```
POST   /api/contracts                   Create contract (status: DRAFT)
GET    /api/contracts                   List current user's contracts
GET    /api/contracts/{id}              Get contract by ID
PUT    /api/contracts/{id}              Update contract
DELETE /api/contracts/{id}             Delete contract
GET    /api/contracts/{id}/pdf          Download contract as PDF
POST   /api/contracts/{id}/finalize     Send for approval (DRAFT → PENDING)
POST   /api/contracts/{id}/approve      Approve contract
POST   /api/contracts/{id}/reject       Reject contract
GET    /api/contracts/pending-approval  List pending approvals
GET    /api/contracts/stats             Contract statistics
```

### Petition / Dilekçe Endpoints (JWT required)

```
POST   /api/petitions                   Create petition (status: DRAFT)
GET    /api/petitions                   List current user's petitions
GET    /api/petitions/{id}              Get petition by ID
PUT    /api/petitions/{id}              Update petition
DELETE /api/petitions/{id}             Delete petition
POST   /api/petitions/{id}/complete     Mark petition as COMPLETED
GET    /api/petitions/{id}/pdf          Download petition as PDF
```

**Create Petition Request body:**
```json
{
  "kurum": "T.C. İstanbul Valiliği",
  "kurumAdresi": "İstanbul, Türkiye",
  "yetkili": "Sayın Vali",
  "konu": "İzin Talebi",
  "govde": "Sayın Yetkili,\n\nBu dilekçe ile ..."
}
```

### Identity Verification Endpoints (JWT required)

```
POST /api/verification/identity   TC Kimlik doğrulama (NFC / MRZ / MANUAL)
GET  /api/verification/status     Giriş yapan kullanıcının doğrulama durumu
```

**POST /api/verification/identity – Request body:**
```json
{
  "tcNo": "12345678901",
  "firstName": "Ali",
  "lastName": "Yılmaz",
  "dateOfBirth": "1990-05-15",
  "method": "MANUAL",
  "mrzData": null
}
```

`method` değerleri: `"NFC"` | `"MRZ"` | `"MANUAL"`

**Response (her iki endpoint):**
```json
{
  "status": "VERIFIED",
  "message": "Kimlik doğrulaması başarıyla tamamlandı.",
  "tcNoMasked": "123******01",
  "firstName": "Ali",
  "lastName": "Yılmaz",
  "verificationMethod": "MANUAL",
  "verifiedAt": "2026-02-21T14:30:00Z",
  "verified": true
}
```

**TC Kimlik Numarası Doğrulama Algoritması (VerificationService.java):**
```
11 haneli, tamamı rakam; ilk hane ≠ 0
d10 = (7*(d[0]+d[2]+d[4]+d[6]+d[8]) - (d[1]+d[3]+d[5]+d[7])) mod 10
d11 = (d[0]+d[1]+...+d[9]) mod 10
Maskeleme: "12345678901" → "123******01"
Ham TC numarası veritabanına HİÇBİR ZAMAN kaydedilmez.
```

### Analysis Endpoints (JWT required)

```
POST /api/analysis/analyze   NLP + GraphRAG pipeline (sözleşme türü tespiti + eksik alan analizi)
```

### Admin Endpoints (ADMIN role required)

```
GET    /api/users        List all users
GET    /api/users/{id}   Get user by ID
PUT    /api/users/{id}   Update user
DELETE /api/users/{id}   Delete user
```

---

## Database Schema

Tables are auto-created/updated by Hibernate (`ddl-auto=update`).

| Tablo | Açıklama |
|-------|----------|
| `users` | Kullanıcı hesapları |
| `contracts` | Sözleşmeler (DRAFT / PENDING / APPROVED / REJECTED / COMPLETED) |
| `petitions` | Dilekçeler (DRAFT / COMPLETED) |
| `identity_verifications` | TC Kimlik doğrulama kayıtları — users ile OneToOne ilişki |

**identity_verifications kolonları:**

| Kolon | Tip | Açıklama |
|-------|-----|----------|
| `id` | BIGSERIAL | PK |
| `user_id` | BIGINT (FK, UNIQUE) | users.id |
| `tc_no_masked` | VARCHAR(20) | "123******01" formatında |
| `first_name` | VARCHAR(100) | |
| `last_name` | VARCHAR(100) | |
| `date_of_birth` | DATE | |
| `verification_method` | VARCHAR(20) | NFC / MRZ / MANUAL |
| `status` | VARCHAR(20) | VERIFIED / FAILED / PENDING |
| `verified_at` | TIMESTAMPTZ | |
| `created_at` | TIMESTAMPTZ | |

---

## Contract Type Mapping

PdfService resolves the correct template based on the contract `type` field:

| Contract Type | Template |
|---------------|----------|
| `kira_sozlesmesi` / `rental` | Kira Sözleşmesi |
| `borc_sozlesmesi` / `other` | Borç Sözleşmesi |
| `hizmet_sozlesmesi` / `service` | Hizmet Sözleşmesi |
| `satis_sozlesmesi` / `sales` | Satış Sözleşmesi |
| `is_sozlesmesi` / `employment` | İş Sözleşmesi |
| `vekaletname` | Vekaletname |
| `taahhutname` | Taahhütname |
| (any other) | Genel Sözleşme (fallback) |

---

## External Services Integration

```properties
services.nlp.base-url=${NLP_SERVICE_URL:http://localhost:8001}
services.graphrag.base-url=${GRAPHRAG_SERVICE_URL:http://localhost:8000}
services.statistics.base-url=${STATISTICS_SERVICE_URL:http://localhost:8002}
```

| Service | Status | Class |
|---------|--------|-------|
| NLP Server | ✅ Implemented | `NlpService.java` |
| GraphRAG Server | ✅ Implemented | `GraphRagService.java` |
| Statistics Server | 🔄 Planned | — |

---

## Logging

Logging is configured in `application.properties`:

```properties
logging.level.com.earzuhal=DEBUG
logging.level.org.springframework.web=INFO
logging.level.org.springframework.security=INFO
logging.level.com.openhtmltopdf=WARN
logging.pattern.console=%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n
```

- `GlobalExceptionHandler` — tüm unhandled exception'lar ERROR seviyesinde + full stack trace
- `PdfService` — her PDF üretimi DEBUG, hata durumunda ERROR
- `VerificationService` — başarılı doğrulama INFO seviyesinde loglanır

---

## Security & Authentication

- **Spring Security 6.x** — Full security framework
- **JWT (JJWT 0.12.6)** — Stateless auth, 24-hour expiration
- **BCrypt** — Password hashing (strength 10)
- **RBAC** — USER and ADMIN roles
- **Input Validation** — Jakarta Validation on all request bodies
- **TC No Masking** — Ham TC kimlik numarası veritabanına kaydedilmez

### Authentication Flow

1. `POST /api/auth/register` → returns JWT
2. `POST /api/auth/login` → returns JWT
3. All protected requests: `Authorization: Bearer <token>`

---

## Troubleshooting

| Problem | Solution |
|---------|---------|
| `POSTGRES_DB_PASSWORD is not set` | Set the env variable before starting |
| `Invalid JWT signature` | Token expired or wrong secret — login again |
| `403 Forbidden` on admin routes | `UPDATE users SET role='ADMIN' WHERE username='...'` then login again |
| PDF shows `?` boxes for Turkish chars | Add Noto Serif fonts to `src/main/resources/fonts/` |
| PDF 500 error | Check server logs — full stack trace in GlobalExceptionHandler |
| `Geçersiz TC Kimlik Numarası` | 11 haneli, algoritmayı geçen geçerli bir TC No kullanın |

---

## Important Security Notes

1. **JWT Secret**: Must be changed for production. Generate with `openssl rand -base64 64`.
2. **Database Password**: Use environment variables, never hardcode.
3. **CORS**: Update `cors.allowed-origins` to your production domains.
4. **HTTPS**: Always use HTTPS in production.
5. **TC No**: Ham TC kimlik numarası veritabanında saklanmaz. Sadece `"123******01"` formatında maskelenmiş hali tutulur.

---

## Maintainer

e-Arzuhal Team — Main Server · Core Backend
