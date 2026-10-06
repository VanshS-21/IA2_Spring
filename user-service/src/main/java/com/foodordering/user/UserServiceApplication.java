package com.foodordering.user;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.foodordering")
public class UserServiceApplication {
    public static void main(String[] x) {
        SpringApplication.run(UserServiceApplication.class, x);
    }
}
