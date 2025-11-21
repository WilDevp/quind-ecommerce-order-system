package com.quind.ecommerce.order.domain.event;

import com.quind.ecommerce.order.domain.vo.OrderId;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evento emitido cuando una orden es cancelada.
 *
 * Este evento es consumido por:
 * - Inventory Service: para liberar el stock reservado
 * - Notification Service: para informar al cliente
 */
public record OrderCancelledEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        OrderId orderId,
        String reason
) implements DomainEvent {

    public static OrderCancelledEvent of(OrderId orderId, String reason) {
        return new OrderCancelledEvent(
                UUID.randomUUID(),
                LocalDateTime.now(),
                orderId,
                reason
        );
    }

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String getEventType() {
        return "order.cancelled";
    }
}
