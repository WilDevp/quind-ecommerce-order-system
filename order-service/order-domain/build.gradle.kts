/**
 * Order Domain - el corazón del servicio de órdenes.
 *
 * Aquí vive la lógica de negocio que determina qué es una orden válida,
 * cómo se crean, cómo cambian de estado, qué reglas se aplican.
 *
 * Mantenemos esto libre de cualquier framework. Si mañana migramos de Spring a Quarkus,
 * este módulo no debe cambiar ni una línea. Esa es la señal de que lo estamos haciendo bien.
 *
 * Lo que va aquí:
 * - Order (aggregate root con su lógica)
 * - OrderItem (entities)
 * - Value Objects (Money, Quantity, Address)
 * - Domain Events (OrderCreatedEvent, OrderConfirmedEvent, etc)
 * - Enums con comportamiento (OrderStatus con su state machine)
 * - Excepciones de dominio (InvalidOrderException, OrderNotFoundException)
 */

plugins {
    java
}

dependencies {
    // Todo heredado del build raíz: Lombok, Validation API, JPA API
    // Si necesitamos agregar algo aquí, nos preguntamos si realmente pertenece al dominio
}
