package com.foodordering.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentControllerTest {
    private PaymentRepository repository;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentRepository.class);
        controller = new PaymentController(repository);
        controller.max = new BigDecimal("50000");
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void simulatedFailureCreatesFailedPayment() {
        when(repository.findByOrderId(10L)).thenReturn(Optional.empty());
        PaymentResponse result = controller.pay(new PaymentRequest(10L, new BigDecimal("100"), "CARD", true));
        assertThat(result.paymentStatus()).isEqualTo("FAILED");
    }

    @Test
    void successfulPaymentIsIdempotent() {
        Payment successful = Payment.of(11L, new BigDecimal("100"), "CARD", PaymentStatus.SUCCESS);
        when(repository.findByOrderId(11L)).thenReturn(Optional.of(successful));
        PaymentResponse result = controller.pay(new PaymentRequest(11L, new BigDecimal("100"), "CARD", false));
        assertThat(result.paymentStatus()).isEqualTo("SUCCESS");
        verify(repository, never()).save(any());
    }
}
