package com.quind.ecommerce.order.domain.event;

import com.quind.ecommerce.order.domain.vo.CustomerId;
import com.quind.ecommerce.order.domain.vo.Money;
import com.quind.ecommerce.order.domain.vo.OrderId;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evento emitido cuando se crea una nueva orden.
 *
 * Este evento es consumido por:
 * - Inventory Service: para reservar stock
 * - Notification Service: para confirmar al cliente
 */
public record OrderCreatedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        OrderId orderId,
        CustomerId customerId,
        Money total,
        int itemCount
) implements DomainEvent {

    public static OrderCreatedEvent of(OrderId orderId, CustomerId customerId, Money total, int itemCount) {
        return new OrderCreatedEvent(
                UUID.randomUUID(),
                LocalDateTime.now(),
                orderId,
                customerId,
                total,
                itemCount
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
        return "order.created";
    }
}
