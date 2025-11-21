package com.quind.ecommerce.order.domain.model;

import com.quind.ecommerce.order.domain.exception.EmptyOrderException;
import com.quind.ecommerce.order.domain.exception.InvalidOrderStateException;
import com.quind.ecommerce.order.domain.vo.CustomerId;
import com.quind.ecommerce.order.domain.vo.Money;
import com.quind.ecommerce.order.domain.vo.OrderId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate Root para Order.
 *
 * Esta es la entidad principal del bounded context de órdenes. Toda modificación
 * a los items de la orden debe pasar por aquí para mantener la consistencia.
 *
 * Implementamos la máquina de estados definida en OrderStatus, validando cada
 * transición antes de ejecutarla.
 */
public class Order {

    private final OrderId orderId;
    private final CustomerId customerId;
    private final List<OrderItem> items;
    private final LocalDateTime createdAt;
    private OrderStatus status;
    private LocalDateTime updatedAt;

    private Order(OrderId orderId, CustomerId customerId, List<OrderItem> items) {
        this.orderId = orderId;
        this.customerId = Objects.requireNonNull(customerId, "CustomerId no puede ser null");
        this.items = new ArrayList<>(items);
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Factory method para crear una nueva orden.
     * Validamos que tenga al menos un item - una orden vacía no tiene sentido.
     */
    public static Order create(CustomerId customerId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new EmptyOrderException();
        }
        return new Order(OrderId.generate(), customerId, items);
    }

    /**
     * Calcula el total sumando los subtotales de todos los items.
     * Delegamos la lógica de suma al Value Object Money.
     */
    public Money getTotal() {
        return items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(Money::add)
                .orElseThrow(() -> new IllegalStateException("La orden debe tener items"));
    }

    /**
     * Confirma la orden. Solo válido desde PENDING.
     */
    public void confirm() {
        transitionTo(OrderStatus.CONFIRMED);
    }

    /**
     * Inicia el procesamiento del pago. Solo válido desde CONFIRMED.
     */
    public void startPaymentProcessing() {
        transitionTo(OrderStatus.PAYMENT_PROCESSING);
    }

    /**
     * Marca la orden como pagada. Solo válido desde PAYMENT_PROCESSING.
     */
    public void markAsPaid() {
        transitionTo(OrderStatus.PAID);
    }

    /**
     * Marca la orden como enviada. Solo válido desde PAID.
     */
    public void ship() {
        transitionTo(OrderStatus.SHIPPED);
    }

    /**
     * Marca la orden como entregada. Solo válido desde SHIPPED.
     */
    public void deliver() {
        transitionTo(OrderStatus.DELIVERED);
    }

    /**
     * Cancela la orden. Solo válido desde PENDING o CONFIRMED.
     */
    public void cancel() {
        if (!status.canBeCancelled()) {
            throw new InvalidOrderStateException(status, OrderStatus.CANCELLED);
        }
        transitionTo(OrderStatus.CANCELLED);
    }

    /**
     * Marca la orden como fallida (error en pago).
     * Solo válido desde PAYMENT_PROCESSING.
     */
    public void markAsFailed() {
        transitionTo(OrderStatus.FAILED);
    }

    /**
     * Método central para transiciones de estado.
     * Validamos la transición usando la máquina de estados de OrderStatus.
     */
    private void transitionTo(OrderStatus newStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw new InvalidOrderStateException(status, newStatus);
        }
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    /**
     * Retornamos una lista inmutable para proteger el invariante.
     * Los items solo se pueden modificar a través de métodos del aggregate.
     */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return String.format("Order{orderId=%s, customerId=%s, status=%s, itemCount=%d}",
                orderId, customerId, status, items.size());
    }
}
