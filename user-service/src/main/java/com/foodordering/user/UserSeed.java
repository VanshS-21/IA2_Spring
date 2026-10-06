package com.foodordering.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class UserSeed {
    @Bean
    CommandLineRunner adminSeed(UserRepository users, PasswordEncoder encoder,
            @Value("${app.admin-password}") String password) {
        return ignored -> {
            if (users.findByEmail("admin@food.com").isEmpty()) {
                users.save(User.make("System Admin", "admin@food.com", encoder.encode(password), "0000000000", Role.ADMIN));
            }
        };
    }
}
