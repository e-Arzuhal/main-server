# e-Arzuhal Main Server Dockerfile
# Multi-stage build using Maven wrapper -> Eclipse Temurin JRE

# ── Build stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper + pom first for dependency layer caching
COPY mvnw ./
COPY .mvn ./.mvn
COPY pom.xml ./

RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Copy sources and build the fat JAR
COPY src ./src
RUN ./mvnw -B clean package -DskipTests \
 && cp target/*.jar /build/app.jar

# ── Runtime stage ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/app.jar /app/app.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
