package com.quind.ecommerce.order.domain.vo;

import java.util.Objects;

/**
 * Value Object que representa el identificador de un producto.
 * Este ID viene del catálogo de productos (bounded context externo).
 */
public final class ProductId {

    private final String value;

    private ProductId(String value) {
        this.value = value;
    }

    public static ProductId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ProductId no puede ser nulo o vacío");
        }
        return new ProductId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductId productId = (ProductId) o;
        return Objects.equals(value, productId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "ProductId{" + value + "}";
    }
}
