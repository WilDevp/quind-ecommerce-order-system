package com.quind.ecommerce.order.domain.model;

/**
 * Estados posibles de una orden con validación de transiciones.
 *
 * Esta es la implementación de la máquina de estados definida en order-state-machine-diagram.md.
 * Las transiciones válidas están codificadas aquí - si no está aquí, no es válido.
 *
 * Flujo normal: PENDING → CONFIRMED → PAYMENT_PROCESSING → PAID → SHIPPED → DELIVERED
 * Flujos alternativos: cancelación desde PENDING/CONFIRMED, fallo desde PAYMENT_PROCESSING
 */
public enum OrderStatus {

    PENDING,
    CONFIRMED,
    PAYMENT_PROCESSING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    FAILED;

    /**
     * Verifica si es válido transicionar desde este estado al nuevo estado.
     * Esta es la lógica central de la máquina de estados.
     */
    public boolean canTransitionTo(OrderStatus newStatus) {
        return switch (this) {
            case PENDING -> newStatus == CONFIRMED || newStatus == CANCELLED;
            case CONFIRMED -> newStatus == PAYMENT_PROCESSING || newStatus == CANCELLED;
            case PAYMENT_PROCESSING -> newStatus == PAID || newStatus == FAILED;
            case PAID -> newStatus == SHIPPED;
            case SHIPPED -> newStatus == DELIVERED;
            case DELIVERED, CANCELLED, FAILED -> false; // Estados terminales
        };
    }

    /**
     * Indica si este es un estado terminal (sin transiciones posibles).
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == FAILED;
    }

    /**
     * Indica si la orden puede ser cancelada desde este estado.
     * Solo se puede cancelar antes de procesar el pago.
     */
    public boolean canBeCancelled() {
        return this == PENDING || this == CONFIRMED;
    }
}
