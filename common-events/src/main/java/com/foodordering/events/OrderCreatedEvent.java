package com.foodordering.events;
import java.math.BigDecimal; import java.time.LocalDateTime;
public record OrderCreatedEvent(Long orderId, Long customerId, BigDecimal totalAmount, String status, LocalDateTime eventTime) {}
