# ============================================
# Multi-stage Dockerfile for Payment Service
# ============================================

# Stage 1: Build
FROM gradle:8.5-jdk17-alpine AS builder

WORKDIR /app

# Copy Gradle files
COPY settings.gradle.kts build.gradle.kts gradlew ./
COPY gradle ./gradle

# Copy source code for all payment-service modules
COPY payment-service/payment-domain ./payment-service/payment-domain
COPY payment-service/payment-application ./payment-service/payment-application
COPY payment-service/payment-infrastructure ./payment-service/payment-infrastructure

# Build the application
RUN ./gradlew :payment-service:payment-infrastructure:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy JAR
COPY --from=builder /app/payment-service/payment-infrastructure/build/libs/payment-service.jar app.jar

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# Run
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD []
