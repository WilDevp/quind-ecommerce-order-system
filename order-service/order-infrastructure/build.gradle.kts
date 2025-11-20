/**
 * Order Infrastructure - toda la plomería técnica.
 *
 * Este es el módulo "sucio" donde viven todos los detalles de implementación:
 * cómo se conecta a Postgres, cómo serializa JSON, cómo produce mensajes a Kafka.
 *
 * Error común: meter lógica de negocio aquí "porque es más fácil".
 * No lo hacemos. Los controllers y repos solo transforman y delegan.
 * Si vemos un if/else con reglas de negocio, probablemente va en domain o application.
 *
 * Lo que va aquí:
 * - REST Controllers (WebFlux) - solo reciben requests y devuelven responses
 * - R2DBC Repositories - implementan las interfaces de OrderRepository
 * - Kafka Producers/Consumers - publican y consumen eventos
 * - MongoDB Event Store - para event sourcing si lo necesitamos
 * - Config classes - toda la configuración de beans de Spring
 * - Application.java - el @SpringBootApplication main
 * - DTOs y Mappers - transforman entre domain objects y JSON
 */

plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    // Referencias a domain y application del mismo servicio
    // Más toda la artillería de infrastructure (WebFlux, R2DBC, Kafka, etc)
    // configurada en el build raíz
}

tasks.bootJar {
    archiveFileName.set("order-service.jar")
}

tasks.bootRun {
    args("--spring.profiles.active=local")
}
