rootProject.name = "quind-ecommerce-order-system"

/**
 * Multi-module setup con Clean Architecture - tres capas por servicio.
 * Muchos proyectos pasa que mezclan las capas y terminan con un acoplamiento horrible.
 * Aquí cada servicio tiene su domain (lógica pura), application (casos de uso) e infrastructure (adaptadores).
 *
 * La razón principal de esta separación: poder testear la lógica de negocio sin levantar Spring,
 * sin base de datos, sin Kafka, sin nada. Solo POJOs y JUnit.
 *
 * Estructura del proyecto:
 * order-service/       -> Todo lo relacionado con órdenes
 * payment-service/     -> Procesamiento de pagos
 * notification-service -> Notificaciones asíncronas
 *
 * Cada uno dividido en: domain, application, infrastructure
 */

// Order Service
include("order-service:order-domain")
include("order-service:order-application")
include("order-service:order-infrastructure")

// Payment Service
include("payment-service:payment-domain")
include("payment-service:payment-application")
include("payment-service:payment-infrastructure")

// Notification Service
include("notification-service:notification-domain")
include("notification-service:notification-application")
include("notification-service:notification-infrastructure")
