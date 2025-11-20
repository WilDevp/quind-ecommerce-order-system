/**
 * Notification Domain - lógica de notificaciones.
 *
 * Este dominio es más simple que Order o Payment porque las notificaciones
 * tienen menos reglas de negocio complejas. Básicamente: se envía o falla.
 *
 * Aún así, hay decisiones importantes: ¿reintentos? ¿prioridades? ¿qué pasa
 * si el email rebota? ¿guardamos historial de notificaciones enviadas?
 *
 * Lo que va aquí:
 * - Notification aggregate (contenido, destinatario, canal)
 * - Value Objects (Email con validación, NotificationChannel enum)
 * - NotificationStatus (PENDING, SENT, FAILED, RETRYING)
 * - Domain Events (NotificationSentEvent, NotificationFailedEvent)
 * - Excepciones de dominio (InvalidEmailException, MaxRetriesExceededException)
 */

plugins {
    java
}

dependencies {
    // Heredado del build raíz
}
