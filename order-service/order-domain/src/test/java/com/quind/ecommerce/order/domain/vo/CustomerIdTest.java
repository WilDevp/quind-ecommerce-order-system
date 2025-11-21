package com.quind.ecommerce.order.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests para CustomerId Value Object.
 */
@DisplayName("CustomerId Value Object")
class CustomerIdTest {

    @Test
    @DisplayName("debe crear CustomerId con UUID válido")
    void shouldCreateCustomerIdWithValidUuid() {
        String uuid = "123e4567-e89b-12d3-a456-426614174000";

        CustomerId customerId = CustomerId.of(uuid);

        assertThat(customerId.getValue()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("debe lanzar excepción con UUID nulo")
    void shouldThrowExceptionWhenUuidIsNull() {
        assertThatThrownBy(() -> CustomerId.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CustomerId");
    }

    @Test
    @DisplayName("debe lanzar excepción con UUID vacío")
    void shouldThrowExceptionWhenUuidIsEmpty() {
        assertThatThrownBy(() -> CustomerId.of("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dos CustomerId con mismo valor deben ser iguales")
    void shouldBeEqualWhenSameValue() {
        String uuid = "123e4567-e89b-12d3-a456-426614174000";

        CustomerId id1 = CustomerId.of(uuid);
        CustomerId id2 = CustomerId.of(uuid);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
