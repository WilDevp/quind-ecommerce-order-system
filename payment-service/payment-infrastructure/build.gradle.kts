/**
 * Payment Infrastructure - adaptadores para pagos.
 *
 * Este módulo es especialmente crítico porque se conecta con servicios externos
 * (Stripe, PayPal, bancos). Cualquier error aquí puede significar dinero perdido.
 *
 * Lecciones aprendidas de producción:
 * - Siempre logeamos los requests/responses a payment gateways (ofuscando datos sensibles)
 * - Usamos idempotency keys - los pagos duplicados son un problema real
 * - Implementamos timeouts agresivos - los gateways pueden colgar
 * - Tenemos fallbacks: si el gateway principal falla, ¿hay un secundario?
 *
 * Lo que va aquí:
 * - REST Controllers (solo para consultas, los pagos vienen por eventos)
 * - Kafka Consumer que escucha OrderCreatedEvent
 * - Kafka Producer que publica PaymentProcessedEvent/PaymentFailedEvent
 * - R2DBC Repository para persistir Payment entities
 * - StripePaymentGatewayAdapter (o el gateway que usemos) - implementa PaymentGatewayPort
 * - Configs de circuit breaker, retry, timeout para llamadas al gateway
 */

plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    // Heredado del build raíz
    // WebFlux viene por defecto para WebClient (llamadas al payment gateway)
}

tasks.bootJar {
    archiveFileName.set("payment-service.jar")
}

tasks.bootRun {
    args("--spring.profiles.active=local")
}
