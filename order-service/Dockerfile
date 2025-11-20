# ============================================
# Multi-stage Dockerfile for Order Service
# ============================================
# Stage 1: Build con Gradle
# Stage 2: Runtime con JRE slim
# ============================================

# Stage 1: Build
FROM gradle:8.5-jdk17-alpine AS builder

WORKDIR /app

# Copy Gradle files
COPY settings.gradle.kts build.gradle.kts gradlew ./
COPY gradle ./gradle

# Copy source code for all order-service modules
COPY order-service/order-domain ./order-service/order-domain
COPY order-service/order-application ./order-service/order-application
COPY order-service/order-infrastructure ./order-service/order-infrastructure

# Build the application (skip tests for faster builds - tests run in CI)
RUN ./gradlew :order-service:order-infrastructure:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy JAR from builder stage
COPY --from=builder /app/order-service/order-infrastructure/build/libs/order-service.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# JVM options can be passed via environment variable
# Example: docker run -e JAVA_OPTS="-Xmx512m" order-service
CMD []
