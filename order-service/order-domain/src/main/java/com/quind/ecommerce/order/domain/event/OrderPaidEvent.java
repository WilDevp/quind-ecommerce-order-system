package com.quind.ecommerce.order.domain.event;

import com.quind.ecommerce.order.domain.vo.Money;
import com.quind.ecommerce.order.domain.vo.OrderId;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evento emitido cuando el pago de una orden se procesa exitosamente.
 *
 * Este evento es consumido por:
 * - Inventory Service: para confirmar la reserva de stock
 * - Fulfillment: para iniciar el proceso de envío
 */
public record OrderPaidEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        OrderId orderId,
        Money amount
) implements DomainEvent {

    public static OrderPaidEvent of(OrderId orderId, Money amount) {
        return new OrderPaidEvent(
                UUID.randomUUID(),
                LocalDateTime.now(),
                orderId,
                amount
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
        return "order.paid";
    }
}
