package com.quind.ecommerce.order.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests para OrderId Value Object.
 *
 * Usamos TDD: primero este test, luego la implementación.
 * Un Value Object debe ser inmutable y comparable por valor, no por referencia.
 */
@DisplayName("OrderId Value Object")
class OrderIdTest {

    @Test
    @DisplayName("debe crear OrderId con UUID válido")
    void shouldCreateOrderIdWithValidUuid() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";

        OrderId orderId = OrderId.of(uuid);

        assertThat(orderId.getValue()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("debe generar OrderId aleatorio")
    void shouldGenerateRandomOrderId() {
        OrderId orderId = OrderId.generate();

        assertThat(orderId).isNotNull();
        assertThat(orderId.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("debe lanzar excepción con UUID nulo")
    void shouldThrowExceptionWhenUuidIsNull() {
        assertThatThrownBy(() -> OrderId.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrderId");
    }

    @Test
    @DisplayName("debe lanzar excepción con UUID vacío")
    void shouldThrowExceptionWhenUuidIsEmpty() {
        assertThatThrownBy(() -> OrderId.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrderId");
    }

    @Test
    @DisplayName("dos OrderId con mismo valor deben ser iguales")
    void shouldBeEqualWhenSameValue() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";

        OrderId orderId1 = OrderId.of(uuid);
        OrderId orderId2 = OrderId.of(uuid);

        assertThat(orderId1).isEqualTo(orderId2);
        assertThat(orderId1.hashCode()).isEqualTo(orderId2.hashCode());
    }

    @Test
    @DisplayName("toString debe retornar el valor del UUID")
    void shouldReturnValueOnToString() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";

        OrderId orderId = OrderId.of(uuid);

        assertThat(orderId.toString()).contains(uuid);
    }
}
