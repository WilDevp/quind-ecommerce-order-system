package com.quind.ecommerce.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests para OrderStatus enum con state machine.
 *
 * La máquina de estados es CRÍTICA para el negocio.
 * Estos tests documentan exactamente qué transiciones son válidas.
 */
@DisplayName("OrderStatus State Machine")
class OrderStatusTest {

    @Test
    @DisplayName("PENDING puede transicionar a CONFIRMED")
    void pendingCanTransitionToConfirmed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
    }

    @Test
    @DisplayName("PENDING puede transicionar a CANCELLED")
    void pendingCanTransitionToCancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("PENDING NO puede transicionar a PAID directamente")
    void pendingCannotTransitionToPaid() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isFalse();
    }

    @Test
    @DisplayName("CONFIRMED puede transicionar a PAYMENT_PROCESSING")
    void confirmedCanTransitionToPaymentProcessing() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PAYMENT_PROCESSING)).isTrue();
    }

    @Test
    @DisplayName("CONFIRMED puede transicionar a CANCELLED")
    void confirmedCanTransitionToCancelled() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("PAYMENT_PROCESSING puede transicionar a PAID")
    void paymentProcessingCanTransitionToPaid() {
        assertThat(OrderStatus.PAYMENT_PROCESSING.canTransitionTo(OrderStatus.PAID)).isTrue();
    }

    @Test
    @DisplayName("PAYMENT_PROCESSING puede transicionar a FAILED")
    void paymentProcessingCanTransitionToFailed() {
        assertThat(OrderStatus.PAYMENT_PROCESSING.canTransitionTo(OrderStatus.FAILED)).isTrue();
    }

    @Test
    @DisplayName("PAYMENT_PROCESSING NO puede transicionar a CANCELLED")
    void paymentProcessingCannotTransitionToCancelled() {
        assertThat(OrderStatus.PAYMENT_PROCESSING.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("PAID puede transicionar a SHIPPED")
    void paidCanTransitionToShipped() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
    }

    @Test
    @DisplayName("SHIPPED puede transicionar a DELIVERED")
    void shippedCanTransitionToDelivered() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("Estados terminales no pueden transicionar a ningún estado")
    @CsvSource({
            "DELIVERED, PENDING",
            "DELIVERED, CONFIRMED",
            "CANCELLED, PENDING",
            "CANCELLED, CONFIRMED",
            "FAILED, PENDING",
            "FAILED, PAID"
    })
    void terminalStatesCannotTransition(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    @DisplayName("debe identificar estados terminales correctamente")
    void shouldIdentifyTerminalStates() {
        assertThat(OrderStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.FAILED.isTerminal()).isTrue();

        assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
        assertThat(OrderStatus.PAID.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("debe identificar estados cancelables")
    void shouldIdentifyCancellableStates() {
        assertThat(OrderStatus.PENDING.canBeCancelled()).isTrue();
        assertThat(OrderStatus.CONFIRMED.canBeCancelled()).isTrue();

        assertThat(OrderStatus.PAYMENT_PROCESSING.canBeCancelled()).isFalse();
        assertThat(OrderStatus.PAID.canBeCancelled()).isFalse();
        assertThat(OrderStatus.SHIPPED.canBeCancelled()).isFalse();
    }
}
