/**
 * Notification Infrastructure - adaptadores para notificaciones.
 *
 * Este servicio NO tiene endpoints de escritura. Solo consume eventos y envía notificaciones.
 * Los endpoints que expone son solo para consultar historial (GET /notifications).
 *
 * Por qué MongoDB: Las notificaciones son write-heavy y no necesitamos joins complejos.
 * Mongo es perfecto para este caso. Guardamos cada notificación como un documento.
 *
 * Error común: intentar enviar emails síncronamente en el consumer de Kafka.
 * Si el email tarda 2 segundos, vamos a bloquear el procesamiento de otros mensajes.
 * Usamos Reactor para paralelizar.
 *
 * Lo que va aquí:
 * - REST Controllers para queries (historial de notificaciones de un usuario)
 * - Kafka Consumers para múltiples eventos (OrderCreated, PaymentProcessed, OrderShipped, etc)
 * - MongoDB Reactive Repository para persistir Notification entities
 * - EmailServiceAdapter (SendGrid, SES, o el que usemos) - implementa EmailServicePort
 * - Templates de emails (podemos usar Thymeleaf o simplemente Strings formateados)
 * - Config para rate limiting (no queremos enviar 10k emails/seg y que nos baneen)
 */

plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    // Heredado del build raíz

    // Descomentamos cuando elijamos el proveedor de email:
    // implementation("com.sendgrid:sendgrid-java:4.10.2")        // SendGrid
    // implementation("software.amazon.awssdk:ses:2.20.0")        // AWS SES
    // implementation("com.mailgun:mailgun-java:1.1.0")           // Mailgun
}

tasks.bootJar {
    archiveFileName.set("notification-service.jar")
}

tasks.bootRun {
    args("--spring.profiles.active=local")
}
