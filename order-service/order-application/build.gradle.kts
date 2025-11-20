/**
 * Order Application - orquestación de casos de uso.
 *
 * Esta capa coordina las operaciones. Toma un comando (CreateOrderCommand),
 * valida permisos, aplica lógica de negocio del dominio, persiste cambios,
 * publica eventos. Pero no sabe SI los datos van a Postgres, Mongo o archivos.
 *
 * La capa de aplicación es el "director de orquesta". Coordina, pero no ejecuta.
 * Usamos ports (interfaces) para persistencia y eventos, implementadas en infrastructure.
 *
 * Lo que va aquí:
 * - Commands y Queries (objetos que representan intenciones)
 * - Handlers (ejecutan los casos de uso)
 * - Application Services (coordinan múltiples agregados)
 * - Ports/Interfaces (OrderRepository, EventPublisher) - solo las interfaces, no implementaciones
 */

plugins {
    java
}

dependencies {
    // Dependencia a order-domain + Spring para DI y Reactor para async
    // Todo configurado en el build raíz
}
