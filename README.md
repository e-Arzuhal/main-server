# e-Arzuhal – Main Server

**Main backend server for the e-Arzuhal platform.**

This Spring Boot application acts as the **central orchestrator** for the e-Arzuhal system.  
It exposes REST APIs for mobile and web clients, coordinates calls to external services (NLP, GraphRAG, statistics, etc.), manages contract workflows, and handles PDF generation and approval flows.

---

## 🧠 System Role

The **Main Server** is responsible for:

- Receiving requests from **Mobile** and **Web** clients
- Acting as a **lightweight API gateway / orchestrator**
- Calling external services:
  - NLP service (Python)
  - GraphRAG / Neo4j service
  - Statistics service
- Merging results from multiple services
- Managing:
  - Users
  - Contracts
  - Approval & digital signature flows
- Generating final **PDF contract documents**
- Applying authentication and authorization (JWT / Spring Security)

> ⚠️ NFC reading, camera scanning, and TC card reading are handled on the **client side** (mobile/web).  
> The backend only validates and processes the received data.

---

## 🏗️ Tech Stack

- **Java:** 21  
- **Spring Boot:** 4.0.2  
- **Build Tool:** Gradle  
- **Database:** PostgreSQL  
- **Architecture:** MVC + Reactive HTTP Clients (WebClient)  
- **Security:** Spring Security  
- **Persistence:** Spring Data JPA  

---

## 📦 Main Dependencies

Key dependencies used in this project:

- spring-boot-starter-webmvc – REST API (MVC)
- spring-boot-starter-webflux – Reactive HTTP client support
- spring-boot-starter-webclient – Service-to-service HTTP calls
- spring-boot-starter-restclient – REST client abstractions
- spring-boot-starter-security – Authentication & authorization
- spring-boot-starter-data-jpa – Persistence layer
- spring-boot-starter-validation – Request validation
- postgresql – Database driver
- lombok – Boilerplate reduction

---

## 🗂️ Project Structure (Suggested)

src/main/java/com/earzuhal  
├── config        # Security, WebClient, application configs  
├── controller    # REST controllers (API layer)  
├── service       # Orchestration & business coordination  
├── client        # HTTP clients for NLP / GraphRAG / Stats  
├── domain        # JPA entities  
├── repository    # JPA repositories  
├── dto           # Request / response models  
└── EarzuhalApplication.java  

---

## ▶️ How to Run

### 1️⃣ Prerequisites

- Java **21**
- PostgreSQL running locally or remotely
- Gradle (or use the wrapper)

---

### 2️⃣ Configure Database

Create a PostgreSQL database (example):

CREATE DATABASE earzuhal;

Update `application.yml` or `application.properties`:

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/earzuhal
    username: earzuhal_user
    password: earzuhal_password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

---

### 3️⃣ Run the Application

Using Gradle wrapper:

./gradlew bootRun

Or build and run:

./gradlew build  
java -jar build/libs/earzuhal-0.0.1-SNAPSHOT.jar

Application will start on:

http://localhost:8080

---

## 🔌 External Services Integration

The main server communicates with other services via **HTTP (REST)**.

Typical flow:

1. Client sends request to Main Server  
2. Main Server calls NLP, GraphRAG and Statistics services  
3. Results are merged  
4. Contract data is finalized  
5. PDF is generated  
6. Approval / digital signature flow begins  

All external service calls are done using **Spring WebClient** (non-blocking).

---

## 🔐 Security

- Spring Security enabled
- Intended to support JWT-based authentication
- Role-based access (USER, ADMIN, OFFICER, etc.)

---

## 🧪 Testing

Run all tests with:

./gradlew test

---

## 🚀 Future Improvements

- Circuit breakers & retries (Resilience4j)
- Async messaging (Kafka / RabbitMQ)
- Object storage for PDFs (S3 / MinIO)
- API documentation (OpenAPI / Swagger)
- Observability (metrics, tracing)
- gRPC for high-throughput NLP calls

---

## 📌 Notes

- This project is designed to be extensible and service-oriented
- Business logic is intentionally kept thin in controllers
- Heavy processing lives in external specialized services

---

## 👤 Maintainer

**e-Arzuhal Team**  
Main Server – Core Backend
