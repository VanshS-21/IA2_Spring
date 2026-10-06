package com.foodordering.payment;
import java.math.BigDecimal; import java.time.LocalDateTime;
public record PaymentResponse(Long paymentId,Long orderId,BigDecimal amount,String paymentStatus,LocalDateTime paymentDate){}
