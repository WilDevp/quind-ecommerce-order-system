package com.quind.ecommerce.order.domain.model;

import com.quind.ecommerce.order.domain.exception.EmptyOrderException;
import com.quind.ecommerce.order.domain.exception.InvalidOrderStateException;
import com.quind.ecommerce.order.domain.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests para Order aggregate root.
 * Organizamos los tests en grupos para mantener claridad.
 */
@DisplayName("Order Aggregate Root")
class OrderTest {

    private CustomerId customerId;
    private OrderItem item1;
    private OrderItem item2;

    @BeforeEach
    void setUp() {
        customerId = CustomerId.of("customer-123");
        item1 = OrderItem.create(
                ProductId.of("prod-1"),
                "Laptop Dell",
                Quantity.of(1),
                Money.of(new BigDecimal("1000.00"), "COP")
        );
        item2 = OrderItem.create(
                ProductId.of("prod-2"),
                "Mouse Logitech",
                Quantity.of(2),
                Money.of(new BigDecimal("50.00"), "COP")
        );
    }

    @Nested
    @DisplayName("Creación de Order")
    class OrderCreation {

        @Test
        @DisplayName("debe crear orden con items válidos")
        void shouldCreateOrderWithValidItems() {
            Order order = Order.create(customerId, List.of(item1, item2));

            assertThat(order.getOrderId()).isNotNull();
            assertThat(order.getCustomerId()).isEqualTo(customerId);
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("debe lanzar excepción con lista de items vacía")
        void shouldThrowExceptionWhenItemsEmpty() {
            assertThatThrownBy(() -> Order.create(customerId, List.of()))
                    .isInstanceOf(EmptyOrderException.class);
        }

        @Test
        @DisplayName("debe calcular total correctamente")
        void shouldCalculateTotalCorrectly() {
            Order order = Order.create(customerId, List.of(item1, item2));

            // 1000.00 + (50.00 * 2) = 1100.00
            assertThat(order.getTotal().getAmount())
                    .isEqualByComparingTo(new BigDecimal("1100.00"));
        }

        @Test
        @DisplayName("debe generar OrderId automáticamente")
        void shouldGenerateOrderIdAutomatically() {
            Order order1 = Order.create(customerId, List.of(item1));
            Order order2 = Order.create(customerId, List.of(item1));

            assertThat(order1.getOrderId()).isNotEqualTo(order2.getOrderId());
        }
    }

    @Nested
    @DisplayName("Transiciones de Estado")
    class StateTransitions {

        @Test
        @DisplayName("debe confirmar orden en estado PENDING")
        void shouldConfirmOrderInPendingState() {
            Order order = Order.create(customerId, List.of(item1));

            order.confirm();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("debe procesar pago de orden confirmada")
        void shouldProcessPaymentForConfirmedOrder() {
            Order order = Order.create(customerId, List.of(item1));
            order.confirm();

            order.startPaymentProcessing();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        }

        @Test
        @DisplayName("debe marcar como pagada orden en procesamiento")
        void shouldMarkAsPaidOrderInProcessing() {
            Order order = Order.create(customerId, List.of(item1));
            order.confirm();
            order.startPaymentProcessing();

            order.markAsPaid();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("debe enviar orden pagada")
        void shouldShipPaidOrder() {
            Order order = Order.create(customerId, List.of(item1));
            order.confirm();
            order.startPaymentProcessing();
            order.markAsPaid();

            order.ship();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("debe entregar orden enviada")
        void shouldDeliverShippedOrder() {
            Order order = Order.create(customerId, List.of(item1));
            order.confirm();
            order.startPaymentProcessing();
            order.markAsPaid();
            order.ship();

            order.deliver();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("debe lanzar excepción al confirmar orden ya confirmada")
        void shouldThrowExceptionWhenConfirmingAlreadyConfirmedOrder() {
            Order order = Order.create(customerId, List.of(item1));
            order.confirm();

            assertThatThrownBy(order::confirm)
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("debe lanzar excepción al enviar orden no pagada")
        void shouldThrowExceptionWhenShippingUnpaidOrder() {
            Order order = Order.create(customerId, List.of(item1));
            order.confirm();

            assertThatThrownBy(order::ship)
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    @Nested
    @DisplayName("Cancelación")
    class Cancellation {

        @Test
        @DisplayName("debe cancelar orden pendiente")
        void shouldCancelPendingOrder() {
            Order order = Order.create(customerId, List.of(item1));

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("debe cancelar orden confirmada")
        void shouldCancelConfirmedOrder() {
            Order order = Order.create(customerId, List.of(item1));
            order.confirm();

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("no debe permitir cancelar orden en procesamiento de pago")
        void shouldNotAllowCancellingOrderInPaymentProcessing() {
            Order order = Order.create(customerId, List.of(item1));
            order.confirm();
            order.startPaymentProcessing();

            assertThatThrownBy(order::cancel)
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    @Nested
    @DisplayName("Fallo de Pago")
    class PaymentFailure {

        @Test
        @DisplayName("debe marcar como fallida orden en procesamiento de pago")
        void shouldMarkAsFailedOrderInPaymentProcessing() {
            Order order = Order.create(customerId, List.of(item1));
            order.confirm();
            order.startPaymentProcessing();

            order.markAsFailed();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        }

        @Test
        @DisplayName("no debe permitir marcar como fallida orden no en procesamiento")
        void shouldNotAllowMarkingAsFailedOrderNotInProcessing() {
            Order order = Order.create(customerId, List.of(item1));
            order.confirm();

            assertThatThrownBy(order::markAsFailed)
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }
}
