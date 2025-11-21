package com.quind.ecommerce.order.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests para Money Value Object.
 *
 * El dinero es crítico - errores de redondeo pueden causar problemas serios.
 * Usamos BigDecimal internamente para evitar errores de punto flotante.
 */
@DisplayName("Money Value Object")
class MoneyTest {

    @Test
    @DisplayName("debe crear Money con monto válido")
    void shouldCreateMoneyWithValidAmount() {
        Money money = Money.of(new BigDecimal("150.50"), "COP");

        assertThat(money.getAmount()).isEqualByComparingTo(new BigDecimal("150.50"));
        assertThat(money.getCurrency()).isEqualTo("COP");
    }

    @Test
    @DisplayName("debe crear Money con valor cero")
    void shouldCreateMoneyWithZero() {
        Money money = Money.zero("COP");

        assertThat(money.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("debe lanzar excepción con monto negativo")
    void shouldThrowExceptionWhenAmountIsNegative() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-10.00"), "COP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    @DisplayName("debe lanzar excepción con monto nulo")
    void shouldThrowExceptionWhenAmountIsNull() {
        assertThatThrownBy(() -> Money.of(null, "COP"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("debe lanzar excepción con moneda nula")
    void shouldThrowExceptionWhenCurrencyIsNull() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("100"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("debe sumar dos Money de la misma moneda")
    void shouldAddMoneyWithSameCurrency() {
        Money money1 = Money.of(new BigDecimal("100.50"), "COP");
        Money money2 = Money.of(new BigDecimal("50.25"), "COP");

        Money result = money1.add(money2);

        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("150.75"));
    }

    @Test
    @DisplayName("debe lanzar excepción al sumar diferentes monedas")
    void shouldThrowExceptionWhenAddingDifferentCurrencies() {
        Money cop = Money.of(new BigDecimal("100"), "COP");
        Money usd = Money.of(new BigDecimal("50"), "USD");

        assertThatThrownBy(() -> cop.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("moneda");
    }

    @Test
    @DisplayName("debe multiplicar Money por cantidad")
    void shouldMultiplyByQuantity() {
        Money unitPrice = Money.of(new BigDecimal("25.00"), "COP");

        Money result = unitPrice.multiply(3);

        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    @DisplayName("debe verificar si es mayor que otro Money")
    void shouldCompareGreaterThan() {
        Money money1 = Money.of(new BigDecimal("100"), "COP");
        Money money2 = Money.of(new BigDecimal("50"), "COP");

        assertThat(money1.isGreaterThan(money2)).isTrue();
        assertThat(money2.isGreaterThan(money1)).isFalse();
    }

    @Test
    @DisplayName("dos Money con mismo valor y moneda deben ser iguales")
    void shouldBeEqualWhenSameValueAndCurrency() {
        Money money1 = Money.of(new BigDecimal("100.00"), "COP");
        Money money2 = Money.of(new BigDecimal("100.00"), "COP");

        assertThat(money1).isEqualTo(money2);
        assertThat(money1.hashCode()).isEqualTo(money2.hashCode());
    }
}
