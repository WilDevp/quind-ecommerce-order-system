package com.quind.ecommerce.order.domain.vo;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object que representa el identificador único de una orden.
 *
 * Usamos un Value Object en lugar de un String simple por varias razones:
 * - Type safety: no podemos confundir un OrderId con un CustomerId accidentalmente
 * - Validación centralizada: las reglas de validación están en un solo lugar
 * - Inmutabilidad: una vez creado, no cambia
 * - Semántica clara: el código expresa intención (OrderId vs String genérico)
 */
public final class OrderId {

    private final String value;

    private OrderId(String value) {
        this.value = value;
    }

    /**
     * Crea un OrderId a partir de un String existente.
     *
     * @param value UUID en formato String
     * @return nueva instancia de OrderId
     * @throws IllegalArgumentException si el valor es nulo o vacío
     */
    public static OrderId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId no puede ser nulo o vacío");
        }
        return new OrderId(value);
    }

    /**
     * Genera un nuevo OrderId con UUID aleatorio.
     * Usamos esto cuando creamos una nueva orden.
     *
     * @return nueva instancia de OrderId con UUID generado
     */
    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderId orderId = (OrderId) o;
        return Objects.equals(value, orderId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "OrderId{" + value + "}";
    }
}
