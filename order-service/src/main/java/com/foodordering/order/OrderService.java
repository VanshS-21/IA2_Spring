package com.foodordering.order;

import com.foodordering.events.OrderCreatedEvent;
import com.foodordering.security.AuthPrincipal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service class OrderService {
    private final OrderRepository orders; private final FoodClient food; private final PaymentClient payment; private final KafkaTemplate<String,OrderCreatedEvent> kafka;
    OrderService(OrderRepository orders, FoodClient food, PaymentClient payment, KafkaTemplate<String,OrderCreatedEvent> kafka) { this.orders=orders; this.food=food; this.payment=payment; this.kafka=kafka; }
    @Transactional(noRollbackFor=ResponseStatusException.class) OrderResponse create(CreateOrderRequest request, AuthPrincipal who) {
        List<OrderItem> items=new ArrayList<>(); BigDecimal total=BigDecimal.ZERO;
        for(CreateItem item:request.items()) { FoodClientResponse foodItem; try { foodItem=food.get(item.foodId()); } catch(FoodNotFoundException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Food "+item.foodId()+" not found"); } catch(Exception ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Food service is unavailable"); }
            if(!foodItem.availability()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Food "+foodItem.foodName()+" is not available");
            if(!request.restaurantId().equals(foodItem.restaurantId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"All foods must belong to the selected restaurant");
            OrderItem snapshot=new OrderItem(); snapshot.foodId=foodItem.foodId(); snapshot.foodName=foodItem.foodName(); snapshot.price=foodItem.price(); snapshot.quantity=item.quantity(); items.add(snapshot); total=total.add(foodItem.price().multiply(BigDecimal.valueOf(item.quantity())));
        }
        FoodOrder order=new FoodOrder(); order.customerId=who.userId(); order.restaurantId=request.restaurantId(); order.totalAmount=total; order.status=OrderStatus.PENDING; for(OrderItem item:items){item.order=order;order.items.add(item);} order=orders.save(order);
        PaymentClientResponse paymentResult; try { paymentResult=payment.pay(new PaymentClientRequest(order.orderId,total,request.paymentMethod()==null?"CARD":request.paymentMethod(),request.simulateFailure())); } catch(Exception ex) { order.status=OrderStatus.PAYMENT_FAILED; orders.save(order); throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payment service is unavailable"); }
        order.paymentId=paymentResult.paymentId(); if(!"SUCCESS".equals(paymentResult.paymentStatus())) { order.status=OrderStatus.PAYMENT_FAILED; orders.save(order); throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,"Payment failed"); }
        order.status=OrderStatus.PLACED; order=orders.save(order); Long id=order.orderId; BigDecimal amount=order.totalAmount;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){kafka.send("order-created",id.toString(),new OrderCreatedEvent(id,who.userId(),amount,"PLACED",LocalDateTime.now())).whenComplete((result,error)->{if(error==null)System.out.println("Published order-created event for order "+id+" partition "+result.getRecordMetadata().partition()+" offset "+result.getRecordMetadata().offset());});}});
        return dto(order,"SUCCESS");
    }
    OrderResponse get(Long id,AuthPrincipal principal){FoodOrder order=find(id);permit(order,principal);return dto(order,order.status==OrderStatus.PLACED?"SUCCESS":order.status.name());}
    List<OrderResponse> customer(Long id,AuthPrincipal principal){if(!"ADMIN".equals(principal.role())&&!id.equals(principal.userId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This is not your order history");return orders.findByCustomerId(id).stream().map(order->dto(order,order.status==OrderStatus.PLACED?"SUCCESS":order.status.name())).toList();}
    Page<OrderResponse> all(Pageable pageable){return orders.findAll(pageable).map(order->dto(order,order.status==OrderStatus.PLACED?"SUCCESS":order.status.name()));}
    @Transactional OrderResponse cancel(Long id,AuthPrincipal principal){FoodOrder order=find(id);permit(order,principal);if(order.status!=OrderStatus.PLACED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Only placed orders can be cancelled");order.status=OrderStatus.CANCELLED;return dto(orders.save(order),"SUCCESS");}
    private FoodOrder find(Long id){return orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));}
    private void permit(FoodOrder order,AuthPrincipal principal){if(!"ADMIN".equals(principal.role())&&!principal.userId().equals(order.customerId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This is not your order");}
    private OrderResponse dto(FoodOrder order,String paymentStatus){return new OrderResponse(order.orderId,order.customerId,order.restaurantId,order.orderDate,order.totalAmount,order.status.name(),paymentStatus,order.items.stream().map(item->new ItemResponse(item.foodId,item.foodName,item.quantity,item.price)).toList());}
}

