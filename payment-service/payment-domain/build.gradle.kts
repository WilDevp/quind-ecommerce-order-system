/**
 * Payment Domain - lógica de procesamiento de pagos.
 *
 * Los pagos son críticos porque involucran dinero real. Aquí va toda la lógica
 * que determina qué hace que un pago sea válido, cómo se procesa, qué puede fallar.
 *
 * Trade-off importante: NO integramos con gateways de pago aquí (Stripe, PayPal, etc).
 * Eso va en infrastructure. El dominio solo sabe que "un pago se procesó" o "falló",
 * no le importa si fue con tarjeta, PayPal, o transferencia bancaria.
 *
 * Lo que va aquí:
 * - Payment aggregate con su lógica (validaciones, reglas de negocio)
 * - Value Objects (Money con manejo de decimales correcto, PaymentMethod)
 * - Domain Events (PaymentProcessedEvent, PaymentFailedEvent, PaymentRefundedEvent)
 * - PaymentStatus enum con transiciones válidas de estado
 * - Excepciones de dominio (InsufficientFundsException, InvalidPaymentMethodException)
 */

plugins {
    java
}

dependencies {
    // Heredado del build raíz
}
