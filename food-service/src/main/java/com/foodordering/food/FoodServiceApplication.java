package com.foodordering.food;
import org.springframework.boot.*; import org.springframework.boot.autoconfigure.SpringBootApplication; import org.springframework.cache.annotation.EnableCaching;
@SpringBootApplication(scanBasePackages="com.foodordering") @EnableCaching public class FoodServiceApplication {public static void main(String[] a){SpringApplication.run(FoodServiceApplication.class,a);}}
