package com.foodordering.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.foodordering.events.OrderCreatedEvent;
import com.foodordering.security.AuthPrincipal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceTest {
    @SuppressWarnings("unchecked")
    @Test
    void unavailableFoodDoesNotCallPaymentOrKafka() {
        OrderRepository orders = mock(OrderRepository.class);
        FoodClient food = mock(FoodClient.class);
        PaymentClient payment = mock(PaymentClient.class);
        KafkaTemplate<String, OrderCreatedEvent> kafka = new KafkaTemplate<>(mock(ProducerFactory.class));
        OrderService service = new OrderService(orders, food, payment, kafka);
        when(food.get(1L)).thenReturn(new FoodClientResponse(1L, "Hidden item", new BigDecimal("100"), "Indian", false, 1L));

        assertThatThrownBy(() -> service.create(
                new CreateOrderRequest(1L, List.of(new CreateItem(1L, 1)), "CARD", false),
                new AuthPrincipal(7L, "customer@example.com", "CUSTOMER")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(payment, orders);
    }

    @SuppressWarnings("unchecked")
    @Test
    void failedPaymentIsPersistedAndDoesNotRegisterAKafkaEvent() {
        OrderRepository orders = mock(OrderRepository.class);
        FoodClient food = mock(FoodClient.class);
        PaymentClient payment = mock(PaymentClient.class);
        KafkaTemplate<String, OrderCreatedEvent> kafka = new KafkaTemplate<>(mock(ProducerFactory.class));
        OrderService service = new OrderService(orders, food, payment, kafka);
        when(food.get(1L)).thenReturn(new FoodClientResponse(1L, "Paneer", new BigDecimal("100"), "Indian", true, 1L));
        when(orders.save(any())).thenAnswer(invocation -> {
            FoodOrder order = invocation.getArgument(0);
            if (order.orderId == null) order.orderId = 99L;
            return order;
        });
        when(payment.pay(any())).thenReturn(new PaymentClientResponse(55L, 99L, new BigDecimal("100"), "FAILED", LocalDateTime.now()));

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.create(
                    new CreateOrderRequest(1L, List.of(new CreateItem(1L, 1)), "CARD", true),
                    new AuthPrincipal(7L, "customer@example.com", "CUSTOMER")))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED));

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            verify(orders, times(2)).save(any(FoodOrder.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
