package com.foodordering.order;

import feign.*; import feign.codec.ErrorDecoder; import jakarta.servlet.http.HttpServletRequest; import org.springframework.cloud.openfeign.FeignClient; import org.springframework.context.annotation.Bean; import org.springframework.web.bind.annotation.*; import org.springframework.web.context.request.*;
@FeignClient(name="food-service") interface FoodClient{@GetMapping("/foods/{id}") FoodClientResponse get(@PathVariable("id") Long id);}
@FeignClient(name="payment-service") interface PaymentClient{@PostMapping("/payments") PaymentClientResponse pay(@RequestBody PaymentClientRequest r);}
class FoodNotFoundException extends RuntimeException { FoodNotFoundException(String message) { super(message); } }
class DownstreamServiceException extends RuntimeException { DownstreamServiceException(String message) { super(message); } }
@org.springframework.context.annotation.Configuration class FeignConfig{
 @Bean RequestInterceptor auth(){return t->{RequestAttributes a=RequestContextHolder.getRequestAttributes();if(a instanceof ServletRequestAttributes s){HttpServletRequest q=s.getRequest();String h=q.getHeader("Authorization"),c=q.getHeader("X-Correlation-Id");if(h!=null)t.header("Authorization",h);if(c!=null)t.header("X-Correlation-Id",c);}};}
 @Bean ErrorDecoder errorDecoder(){return (methodKey,response)->{if(response.status()==404&&methodKey.startsWith("FoodClient"))return new FoodNotFoundException("Requested food was not found");return new DownstreamServiceException("Downstream service returned HTTP "+response.status());};}
}
