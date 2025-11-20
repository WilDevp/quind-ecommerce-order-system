# ============================================
# Multi-stage Dockerfile for Notification Service
# ============================================

# Stage 1: Build
FROM gradle:8.5-jdk17-alpine AS builder

WORKDIR /app

# Copy Gradle files
COPY settings.gradle.kts build.gradle.kts gradlew ./
COPY gradle ./gradle

# Copy source code for all notification-service modules
COPY notification-service/notification-domain ./notification-service/notification-domain
COPY notification-service/notification-application ./notification-service/notification-application
COPY notification-service/notification-infrastructure ./notification-service/notification-infrastructure

# Build the application
RUN ./gradlew :notification-service:notification-infrastructure:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy JAR
COPY --from=builder /app/notification-service/notification-infrastructure/build/libs/notification-service.jar app.jar

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

# Run
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD []
