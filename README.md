# e-Arzuhal – Main Server

Main backend server for the e-Arzuhal platform.

Bu Spring Boot uygulaması e-Arzuhal sisteminin merkezi orkestratörüdür.
Mobil ve web istemcilerine REST API sunar; NLP, GraphRAG ve istatistik servislerine çağrı yapar;
sözleşme/dilekçe iş akışlarını yönetir; PDF üretir ve kimlik doğrulama yapar.

---

## Tech Stack

| Katman | Teknoloji |
|--------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.2 |
| Build | Maven |
| Database | PostgreSQL |
| Security | Spring Security 6 + JWT (JJWT 0.12.6) |
| Persistence | Spring Data JPA + Hibernate |
| PDF Generation | openhtmltopdf-pdfbox 1.0.10 |
| Template Engine | Thymeleaf |
| HTTP Client | Spring WebFlux WebClient |
| API Docs | springdoc-openapi 3.0.0 (Spring Boot 4.x uyumlu) |

---

## Proje Yapısı

```
src/main/java/com/earzuhal/
├── config/
│   ├── SecurityConfig.java
│   ├── JwtConfig.java
│   ├── WebConfig.java
│   └── WebClientConfig.java
├── security/jwt/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── JwtAuthenticationEntryPoint.java
├── Controller/
│   ├── AuthController.java          # /api/auth/**
│   ├── ContractController.java      # /api/contracts/**
│   ├── PetitionController.java      # /api/petitions/**
│   ├── UserController.java          # /api/users/** + /api/users/lookup
│   ├── AnalysisController.java      # /api/analysis/**
│   ├── VerificationController.java  # /api/verification/**
│   ├── DisclaimerController.java    # /api/disclaimer/**
│   ├── ChatbotController.java       # /api/chatbot/**
│   └── LandingController.java       # /api/landing/**
├── Service/
│   ├── AuthService.java
│   ├── UserService.java             # lookupByTcKimlik dahil
│   ├── ContractService.java         # TC Kimlik doğrulama + idempotency
│   ├── PetitionService.java
│   ├── PdfService.java
│   ├── AnalysisService.java
│   ├── NlpService.java
│   ├── GraphRagService.java
│   └── VerificationService.java
├── Repository/
│   ├── UserRepository.java          # findByTcKimlik dahil
│   ├── ContractRepository.java
│   └── ...
├── Model/
│   ├── User.java                    # tc_kimlik alanı eklendi
│   ├── Contract.java                # counterparty_tc_kimlik alanı eklendi
│   └── ...
└── dto/
    ├── contract/                    # ContractRequest/Response'da counterpartyTcKimlik
    └── ...

src/main/resources/
├── application.properties
└── templates/pdf/
    ├── contracts/
    │   ├── kira_sozlesmesi.html
    │   ├── borc_sozlesmesi.html
    │   ├── hizmet_sozlesmesi.html
    │   ├── satis_sozlesmesi.html
    │   ├── is_sozlesmesi.html
    │   ├── vekaletname.html
    │   ├── taahhutname.html
    │   └── genel_sozlesme.html      # fallback
    └── petitions/
        └── dilekce.html
```

---

## Kurulum

### 1. Veritabanı

```sql
CREATE DATABASE earzuhal_main_server;
```

### 2. Ortam Değişkenleri

`.env.example` → `.env` olarak kopyalayıp doldur:

| Değişken | Zorunlu | Açıklama |
|----------|---------|----------|
| `POSTGRES_DB_PASSWORD` | ✅ | PostgreSQL şifresi |
| `JWT_SECRET` | ✅ | Min. 64 karakter. `openssl rand -base64 64` |
| `INTERNAL_API_KEY` | Prod ✅ | Python servislerle paylaşılan anahtar. `openssl rand -hex 32` |
| `CORS_ALLOWED_ORIGINS` | Prod ✅ | Virgülle ayrılmış frontend origin'leri |
| `NLP_SERVICE_URL` | ❌ | Varsayılan: `http://localhost:8001` |
| `GRAPHRAG_SERVICE_URL` | ❌ | Varsayılan: `http://localhost:8000` |
| `STATISTICS_SERVICE_URL` | ❌ | Varsayılan: `http://localhost:8002` |
| `CHATBOT_SERVICE_URL` | ❌ | Varsayılan: `http://localhost:8003` |

⚠️ `.env` dosyasını asla commit etmeyin!

### 3. Build & Çalıştır

```bash
cd main-server
./mvnw spring-boot:run
```

Uygulama: `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## API Endpoints

### Public

```
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
```

### Kullanıcı (JWT gerekli)

```
GET  /api/users/me
PUT  /api/users/me
GET  /api/users/lookup?tcKimlik=   → { "found": true, "displayName": "Ad Soyad" }
                                     { "found": false }
```

### Sözleşme (JWT gerekli)

```
POST   /api/contracts
GET    /api/contracts
GET    /api/contracts/{id}
PUT    /api/contracts/{id}
DELETE /api/contracts/{id}
GET    /api/contracts/{id}/pdf
POST   /api/contracts/{id}/finalize     DRAFT → PENDING
POST   /api/contracts/{id}/approve      TC Kimlik doğrulama + idempotency
POST   /api/contracts/{id}/reject       TC Kimlik doğrulama + idempotency
GET    /api/contracts/pending-approval
GET    /api/contracts/stats
GET    /api/contracts/{id}/explanation
```

**Sözleşme oluşturma gövdesi:**
```json
{
  "title": "Kira Sözleşmesi",
  "type": "kira_sozlesmesi",
  "content": "...",
  "amount": "15.000 TL",
  "counterpartyName": "Ahmet Yılmaz",
  "counterpartyRole": "Kiracı",
  "counterpartyTcKimlik": "12345678901"
}
```

### Dilekçe (JWT gerekli)

```
POST   /api/petitions
GET    /api/petitions
GET    /api/petitions/{id}
PUT    /api/petitions/{id}
DELETE /api/petitions/{id}
POST   /api/petitions/{id}/complete
GET    /api/petitions/{id}/pdf
```

### Kimlik Doğrulama (JWT gerekli)

```
POST /api/verification/identity   { tcNo, firstName, lastName, dateOfBirth, method }
GET  /api/verification/status
```

`method` değerleri: `"NFC"` | `"MRZ"` | `"MANUAL"`

### Analiz (JWT gerekli)

```
POST /api/analysis/analyze   NLP + GraphRAG pipeline
```

### Disclaimer (JWT gerekli)

```
GET  /api/disclaimer/status
POST /api/disclaimer/accept   { "platform": "WEB" | "MOBILE" }
```

### Admin (ADMIN rolü gerekli)

```
GET    /api/users
GET    /api/users/{id}
PUT    /api/users/{id}
DELETE /api/users/{id}
```

---

## Veritabanı Şeması

Tablolar Hibernate tarafından otomatik oluşturulur (`ddl-auto=update`).

| Tablo | Açıklama |
|-------|----------|
| `users` | `tc_kimlik VARCHAR(11) UNIQUE` dahil |
| `contracts` | `counterparty_tc_kimlik VARCHAR(11)` dahil |
| `petitions` | Dilekçeler |
| `identity_verifications` | NFC/MRZ/Manuel doğrulama kayıtları |
| `disclaimer_acceptances` | Yasal uyarı kabul kayıtları |
| `revoked_tokens` | JWT blacklist |

---

## Sözleşme Tipi Eşleşmesi

| Tür | Şablon |
|-----|--------|
| `kira_sozlesmesi` / `rental` | Kira Sözleşmesi |
| `borc_sozlesmesi` / `other` | Borç Sözleşmesi |
| `hizmet_sozlesmesi` / `service` | Hizmet Sözleşmesi |
| `satis_sozlesmesi` / `sales` | Satış Sözleşmesi |
| `is_sozlesmesi` / `employment` | İş Sözleşmesi |
| `vekaletname` | Vekaletname |
| `taahhutname` | Taahhütname |
| (diğer) | Genel Sözleşme (fallback) |

---

## Güvenlik

- **JWT** — 24 saatlik token, `jti` ile blacklist
- **BCrypt** — Şifre hash (strength 10)
- **IDOR Koruması** — Her işlemde sahiplik doğrulaması (404 maskeleme)
- **TC Kimlik** — Ham numara veritabanına kaydedilmez; `"123******01"` formatında maskelenir
- **Onay Doğrulaması** — `counterpartyTcKimlik` varsa onaylayan kullanıcının `tcKimlik`'i eşleşmeli
- **İdempotency** — Zaten sonuçlanmış sözleşmeye onay/red isteği `400 Bad Request` döner
- **Kendi Kendine Onay** — Sözleşme sahibi kendi sözleşmesini onaylayamaz
- **Disclaimer Kapısı** — Finalize için yasal uyarı kabul zorunlu
- **Internal API Key** — Python servislere `X-Internal-API-Key` header

---

## Sorun Giderme

| Problem | Çözüm |
|---------|-------|
| `POSTGRES_DB_PASSWORD is not set` | `.env` dosyasını kontrol et |
| `Invalid JWT signature` | Yeniden giriş yap |
| `403 Forbidden` admin route | `UPDATE users SET role='ADMIN' WHERE username='...'` |
| PDF'de `?` kutucukları | `src/main/resources/fonts/` klasörüne Noto Serif ekle |
| `Geçersiz TC Kimlik Numarası` | 11 haneli, checksum algoritmasını geçen numara kullan |

---

## Maintainer

e-Arzuhal Team — Main Server · Core Backend
