package com.quind.ecommerce.order.domain.exception;

import com.quind.ecommerce.order.domain.model.OrderStatus;

/**
 * Excepción lanzada cuando se intenta una transición de estado inválida.
 * Por ejemplo: pasar de PENDING a PAID directamente sin confirmar primero.
 */
public class InvalidOrderStateException extends DomainException {

    private final OrderStatus currentStatus;
    private final OrderStatus targetStatus;

    public InvalidOrderStateException(OrderStatus currentStatus, OrderStatus targetStatus) {
        super(String.format(
                "Transición de estado inválida: no se puede pasar de %s a %s",
                currentStatus, targetStatus
        ));
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getTargetStatus() {
        return targetStatus;
    }
}
