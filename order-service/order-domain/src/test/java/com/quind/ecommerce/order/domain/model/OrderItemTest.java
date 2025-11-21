package com.quind.ecommerce.order.domain.model;

import com.quind.ecommerce.order.domain.vo.Money;
import com.quind.ecommerce.order.domain.vo.ProductId;
import com.quind.ecommerce.order.domain.vo.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests para OrderItem entity.
 */
@DisplayName("OrderItem Entity")
class OrderItemTest {

    @Test
    @DisplayName("debe crear OrderItem con datos válidos")
    void shouldCreateOrderItemWithValidData() {
        ProductId productId = ProductId.of("prod-123");
        String productName = "Laptop Dell XPS";
        Quantity quantity = Quantity.of(2);
        Money unitPrice = Money.of(new BigDecimal("1500000"), "COP");

        OrderItem item = OrderItem.create(productId, productName, quantity, unitPrice);

        assertThat(item.getProductId()).isEqualTo(productId);
        assertThat(item.getProductName()).isEqualTo(productName);
        assertThat(item.getQuantity()).isEqualTo(quantity);
        assertThat(item.getUnitPrice()).isEqualTo(unitPrice);
    }

    @Test
    @DisplayName("debe calcular subtotal correctamente")
    void shouldCalculateSubtotalCorrectly() {
        ProductId productId = ProductId.of("prod-123");
        Quantity quantity = Quantity.of(3);
        Money unitPrice = Money.of(new BigDecimal("100.00"), "COP");

        OrderItem item = OrderItem.create(productId, "Test Product", quantity, unitPrice);

        assertThat(item.getSubtotal().getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("debe lanzar excepción con nombre de producto vacío")
    void shouldThrowExceptionWhenProductNameIsEmpty() {
        ProductId productId = ProductId.of("prod-123");
        Quantity quantity = Quantity.of(1);
        Money unitPrice = Money.of(new BigDecimal("100"), "COP");

        assertThatThrownBy(() -> OrderItem.create(productId, "", quantity, unitPrice))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
