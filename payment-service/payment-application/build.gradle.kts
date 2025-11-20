/**
 * Payment Application - orquestación de pagos.
 *
 * Este servicio escucha eventos de OrderCreated, procesa el pago, y responde con
 * PaymentProcessed o PaymentFailed. Es reactivo porque los pagos pueden tardar
 * (llamadas a APIs externas, autorizaciones bancarias, etc).
 *
 * Importante: aquí van los retry policies y circuit breakers para llamadas a payment gateways.
 * Si Stripe está caído, no queremos tumbar todo el sistema.
 *
 * Lo que va aquí:
 * - ProcessPaymentCommand con toda su validación
 * - ProcessPaymentHandler que coordina el flujo
 * - Queries para consultar estado de pagos
 * - PaymentRepository port (interface)
 * - PaymentGatewayPort (interface para Stripe/PayPal/etc)
 */

plugins {
    java
}

dependencies {
    // Heredado del build raíz
}
