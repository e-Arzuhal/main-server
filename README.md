# e-Arzuhal – Main Server

Main backend server for the e-Arzuhal platform.

This Spring Boot application acts as the central orchestrator for the e-Arzuhal system.
It exposes REST APIs for mobile and web clients, coordinates calls to external services
(NLP, GraphRAG, statistics, etc.), manages contract workflows, and handles PDF generation
and approval flows.

---

## System Role

The Main Server is responsible for:

- Receiving requests from Mobile and Web clients
- Acting as a lightweight API gateway / orchestrator
- Calling external services:
  - NLP service
  - GraphRAG / Knowledge Graph service
  - Statistics service
- Merging results from multiple services
- Managing:
  - Users
  - Contracts
  - Approval and digital signature flows
- Generating final PDF contract documents
- Applying authentication and authorization (JWT / Spring Security)

NOTE:
NFC reading, camera scanning, and TC identity card reading are handled on the client side
(mobile or web). The backend only validates and processes the received data.

---

## Tech Stack

- Java: 21
- Spring Boot: 4.0.2
- Build Tool: Maven
- Database: PostgreSQL
- Architecture: MVC + Reactive HTTP Clients (WebClient)
- Security: Spring Security
- Persistence: Spring Data JPA

---

## Main Dependencies

Key dependencies used in this project:

- spring-boot-starter-web (REST API - MVC)
- spring-boot-starter-security (Authentication and authorization)
- spring-boot-starter-data-jpa (Persistence layer)
- spring-boot-starter-validation (Request validation)
- jjwt-api, jjwt-impl, jjwt-jackson 0.12.6 (JWT token generation and validation)
- postgresql (Database driver)
- lombok (Boilerplate reduction)

---

## Project Structure

```
src/main/java/com/earzuhal/
├── config/                    # Configuration classes
│   ├── SecurityConfig.java    # Spring Security + JWT configuration
│   ├── JwtConfig.java         # JWT properties
│   └── WebConfig.java         # CORS configuration
├── security/                  # Security components
│   ├── jwt/                   # JWT token handling
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   └── JwtAuthenticationEntryPoint.java
│   └── CustomUserDetailsService.java
├── controller/                # REST API endpoints
│   ├── AuthController.java   # /api/auth/register, /api/auth/login
│   └── UserController.java   # /api/users/**
├── service/                   # Business logic
│   ├── AuthService.java      # Registration, login logic
│   └── UserService.java      # User management
├── repository/                # Data access layer
│   └── UserRepository.java
├── Model/                     # JPA entities
│   └── User.java
├── dto/                       # Data Transfer Objects
│   ├── auth/                  # Authentication DTOs
│   └── user/                  # User DTOs
├── exception/                 # Exception handling
│   ├── GlobalExceptionHandler.java
│   └── Custom exceptions...
└── MainServerApp.java
```

---

## How to Run

### Prerequisites

- Java 21
- PostgreSQL running locally or remotely
- Maven 3.9+

---

### 1. Configure Database

Create a PostgreSQL database:

```sql
CREATE DATABASE earzuhal_main_server;
```

---

### 2. Set Environment Variables

**CRITICAL**: Before running the application, you MUST set these environment variables:

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

**Generate a secure JWT_SECRET:**
```bash
# Linux/Mac
openssl rand -base64 64

# Or use any strong random string (minimum 512 bits for HS512)
```

⚠️ **NEVER commit JWT_SECRET to version control!**

---

### 3. Install Dependencies

```bash
cd main-server
./mvnw clean install
```

---

### 4. Run the Application

Using Maven wrapper:

```bash
./mvnw spring-boot:run
```

Or build and run JAR:

```bash
./mvnw clean package
java -jar target/main_server-0.0.1-SNAPSHOT.jar
```

Application will start on:

```
http://localhost:8080
```

---

## External Services Integration

The main server communicates with other services via HTTP (REST).

Typical flow:

1. Client sends request to Main Server
2. Main Server calls NLP, GraphRAG, and Statistics services
3. Results are merged
4. Contract data is finalized
5. PDF is generated
6. Approval and digital signature flow begins

All external service calls are done using Spring WebClient (non-blocking).

---

## Security & Authentication

### Implementation Status: ✅ COMPLETE

The application implements comprehensive security using:

- **Spring Security 6.x** - Full security framework
- **JWT (JSON Web Tokens)** - Stateless authentication
- **BCrypt Password Hashing** - Secure password storage (strength 10)
- **Role-Based Access Control (RBAC)** - USER and ADMIN roles
- **Global Exception Handling** - Consistent error responses
- **Input Validation** - Jakarta Validation on all endpoints

### Authentication Flow

1. **Registration**: User registers via `/api/auth/register`
   - Password is hashed with BCrypt
   - User is saved with role "USER"
   - JWT token is generated and returned

2. **Login**: User logs in via `/api/auth/login`
   - Credentials are validated
   - JWT token is generated and returned
   - Token expires in 24 hours

3. **Protected Requests**: Client includes token in Authorization header
   - `Authorization: Bearer <jwt-token>`
   - Token is validated on each request
   - User context is set in SecurityContext

### Roles

- **USER**: Can access own profile (`/api/users/me`)
- **ADMIN**: Can manage all users (`/api/users/**`)

---

## API Endpoints

### Public Endpoints (No Authentication Required)

```
POST /api/auth/register  - Register new user
POST /api/auth/login     - Login and get JWT token
```

### Authenticated Endpoints (Requires valid JWT token)

```
GET  /api/users/me       - Get current user profile
PUT  /api/users/me       - Update current user profile
```

### Admin-Only Endpoints (Requires ADMIN role)

```
GET    /api/users        - List all users
GET    /api/users/{id}   - Get user by ID
PUT    /api/users/{id}   - Update user
DELETE /api/users/{id}   - Delete user
```

---

## Testing the API

### 1. Register a New User

```bash
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "username": "testuser",
  "email": "test@example.com",
  "password": "Test1234",
  "firstName": "Test",
  "lastName": "User"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "userInfo": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "firstName": "Test",
    "lastName": "User",
    "role": "USER",
    "isActive": true
  }
}
```

### 2. Login

```bash
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "usernameOrEmail": "testuser",
  "password": "Test1234"
}
```

### 3. Access Protected Endpoint

```bash
GET http://localhost:8080/api/users/me
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

### 4. Create First Admin User

After registering a user, update their role in the database:

```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'adminuser';
```

Then login again to get a new token with ADMIN role.

---

## Testing

Run all tests with:

mvn test

---

## Future Improvements

### Security Enhancements
- JWT refresh token mechanism
- Token blacklist (for logout)
- Email verification on registration
- Password reset via email
- Account lockout after failed login attempts
- Two-factor authentication (2FA)
- Social OAuth2 login (Google, GitHub)
- Rate limiting on authentication endpoints

### Infrastructure
- Circuit breakers and retries (Resilience4j)
- Async messaging (Kafka or RabbitMQ)
- Object storage for PDFs (S3 or MinIO)
- API documentation (OpenAPI / Swagger)
- Observability (metrics and tracing)
- gRPC for high-throughput NLP calls
- Redis caching for performance

### Features
- User profile pictures
- Audit logging for security events
- Soft delete for users
- Multi-language support
- Advanced search and filtering

---

## Important Security Notes

⚠️ **Production Deployment Checklist:**

1. **JWT Secret**: MUST be changed to a strong, random value (512+ bits)
   - Generate with: `openssl rand -base64 64`
   - Store in environment variables or secrets manager
   - NEVER commit to version control

2. **Database Password**: Use strong passwords
   - Store in environment variables
   - Use different credentials for dev/prod

3. **CORS**: Update `cors.allowed-origins` in application.properties
   - Only allow trusted frontend domains
   - Never use `*` (allow all) in production

4. **HTTPS**: Always use HTTPS in production
   - JWT tokens can be intercepted over HTTP
   - Use a reverse proxy (nginx, Caddy) for TLS termination

5. **Token Expiration**: Current setting is 24 hours
   - Adjust based on security requirements
   - Consider implementing refresh tokens for longer sessions

6. **Rate Limiting**: Consider adding rate limiting to prevent brute force attacks

---

## Troubleshooting

### "POSTGRES_DB_PASSWORD is not set"
- Set the environment variable before running
- Check your shell session

### "Invalid JWT signature" or "Expired JWT token"
- Token has expired (24 hours)
- Login again to get a new token
- Check JWT_SECRET is consistent

### "403 Forbidden" on admin endpoints
- Your user role is not ADMIN
- Update role in database: `UPDATE users SET role = 'ADMIN' WHERE username = 'youruser';`
- Login again to refresh token

### IDE shows compilation errors
- Run `./mvnw clean install` first
- Spring Security may show false positives in IDE
- Trust Maven build over IDE errors

---

## Configuration Files

### application.properties

Current configuration:
- Server port: 8080
- Database: PostgreSQL on localhost:5432
- JWT token expiration: 24 hours
- CORS origins: localhost:3000, localhost:4200

To customize, edit: `src/main/resources/application.properties`

---

## Notes

- This project is designed to be extensible and service-oriented
- Business logic is intentionally kept thin in controllers
- Heavy processing lives in external specialized services
- Password are NEVER returned in API responses (DTO pattern)
- All sensitive operations require authentication
- ADMIN role required for user management operations

---

## Maintainer

e-Arzuhal Team
Main Server – Core Backend
