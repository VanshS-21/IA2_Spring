package com.foodordering.order; import jakarta.persistence.*; import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.*;
@Entity @Table(name="orders") class FoodOrder {@Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long orderId; Long customerId; Long restaurantId; LocalDateTime orderDate=LocalDateTime.now(); BigDecimal totalAmount; @Enumerated(EnumType.STRING) OrderStatus status; Long paymentId; @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) List<OrderItem> items=new ArrayList<>();}
@Entity class OrderItem {@Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long orderItemId; Long foodId; String foodName; int quantity; BigDecimal price; @ManyToOne FoodOrder order;}
enum OrderStatus{PENDING,PLACED,PAYMENT_FAILED,CANCELLED}
