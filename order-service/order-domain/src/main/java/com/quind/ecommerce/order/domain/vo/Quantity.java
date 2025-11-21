package com.quind.ecommerce.order.domain.vo;

import java.util.Objects;

/**
 * Value Object que representa una cantidad de productos.
 * Siempre debe ser un entero positivo.
 */
public final class Quantity {

    private final int value;

    private Quantity(int value) {
        this.value = value;
    }

    public static Quantity of(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }
        return new Quantity(value);
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quantity quantity = (Quantity) o;
        return value == quantity.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
