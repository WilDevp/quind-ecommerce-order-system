package com.quind.ecommerce.order.domain.vo;

import java.util.Objects;

/**
 * Value Object que representa el identificador de un cliente.
 *
 * Este ID viene del servicio de clientes (bounded context externo).
 * No generamos CustomerIds aquí, solo los recibimos y validamos.
 */
public final class CustomerId {

    private final String value;

    private CustomerId(String value) {
        this.value = value;
    }

    public static CustomerId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CustomerId no puede ser nulo o vacío");
        }
        return new CustomerId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerId that = (CustomerId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "CustomerId{" + value + "}";
    }
}
