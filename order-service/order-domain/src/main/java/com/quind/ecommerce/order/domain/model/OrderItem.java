package com.quind.ecommerce.order.domain.model;

import com.quind.ecommerce.order.domain.vo.Money;
import com.quind.ecommerce.order.domain.vo.ProductId;
import com.quind.ecommerce.order.domain.vo.Quantity;

import java.util.Objects;

/**
 * Entity que representa un item dentro de una orden.
 *
 * No es un Value Object porque tiene identidad propia dentro del contexto
 * de la orden (podríamos tener dos items del mismo producto con diferentes
 * cantidades o precios si hubo cambios de precio).
 */
public class OrderItem {

    private final ProductId productId;
    private final String productName;
    private final Quantity quantity;
    private final Money unitPrice;

    private OrderItem(ProductId productId, String productName, Quantity quantity, Money unitPrice) {
        this.productId = Objects.requireNonNull(productId, "ProductId no puede ser null");
        this.productName = validateProductName(productName);
        this.quantity = Objects.requireNonNull(quantity, "Quantity no puede ser null");
        this.unitPrice = Objects.requireNonNull(unitPrice, "UnitPrice no puede ser null");
    }

    /**
     * Factory method para crear un OrderItem validado.
     * Usamos este patrón para encapsular las validaciones.
     */
    public static OrderItem create(ProductId productId, String productName, Quantity quantity, Money unitPrice) {
        return new OrderItem(productId, productName, quantity, unitPrice);
    }

    private String validateProductName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío");
        }
        return name;
    }

    /**
     * Calcula el subtotal del item: cantidad × precio unitario.
     * Delegamos la multiplicación al Value Object Money.
     */
    public Money getSubtotal() {
        return unitPrice.multiply(quantity.getValue());
    }

    public ProductId getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItem orderItem = (OrderItem) o;
        return Objects.equals(productId, orderItem.productId) &&
               Objects.equals(productName, orderItem.productName) &&
               Objects.equals(quantity, orderItem.quantity) &&
               Objects.equals(unitPrice, orderItem.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, productName, quantity, unitPrice);
    }

    @Override
    public String toString() {
        return String.format("OrderItem{productId=%s, productName='%s', quantity=%s, unitPrice=%s}",
                productId, productName, quantity, unitPrice);
    }
}
