package com.quind.ecommerce.order.domain.event;

import com.quind.ecommerce.order.domain.vo.OrderId;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evento emitido cuando una orden es confirmada.
 *
 * Indica que el cliente ha aceptado la orden y se puede proceder
 * con el procesamiento del pago.
 */
public record OrderConfirmedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        OrderId orderId
) implements DomainEvent {

    public static OrderConfirmedEvent of(OrderId orderId) {
        return new OrderConfirmedEvent(
                UUID.randomUUID(),
                LocalDateTime.now(),
                orderId
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
        return "order.confirmed";
    }
}
