package com.quind.ecommerce.order.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Interface base para todos los eventos de dominio.
 *
 * Los eventos de dominio capturan algo que ocurrió en el dominio que es
 * relevante para otros bounded contexts o para triggers asíncronos.
 */
public interface DomainEvent {

    /**
     * Identificador único del evento para idempotencia.
     */
    UUID getEventId();

    /**
     * Momento exacto en que ocurrió el evento.
     */
    LocalDateTime getOccurredAt();

    /**
     * Nombre del tipo de evento para serialización/routing.
     */
    String getEventType();
}
