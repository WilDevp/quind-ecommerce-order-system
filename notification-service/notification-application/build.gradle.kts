/**
 * Notification Application - orquestación de notificaciones.
 *
 * Este servicio es 100% reactivo a eventos. Escucha todo lo que pasa en el sistema
 * (OrderCreated, PaymentProcessed, OrderShipped) y envía las notificaciones correspondientes.
 *
 * Decisión de diseño: notificaciones asíncronas. No bloqueamos el flujo principal
 * esperando que se envíe un email. Si falla, reintentamos después.
 *
 * Estrategia de reintentos: exponential backoff. No queremos hacer DDoS a SendGrid
 * si tienen un problema temporal.
 *
 * Lo que va aquí:
 * - SendNotificationCommand (triggered por eventos de otros servicios)
 * - SendNotificationHandler con lógica de reintentos
 * - Queries para historial de notificaciones
 * - NotificationRepository port
 * - EmailServicePort (interface para SendGrid/SES/Mailgun)
 */

plugins {
    java
}

dependencies {
    // Heredado del build raíz
}
