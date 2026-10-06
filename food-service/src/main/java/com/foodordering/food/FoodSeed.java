package com.foodordering.food;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FoodSeed {
    @Bean
    CommandLineRunner seedInitialMenu(RestaurantRepository restaurants, FoodRepository foods) {
        return ignored -> {
            Restaurant indian = restaurants.findById(1L)
                    .orElseGet(() -> restaurants.save(Restaurant.of("Spice Garden", "Central Market")));
            Restaurant pizza = restaurants.findById(2L)
                    .orElseGet(() -> restaurants.save(Restaurant.of("Pizza Planet", "West End")));

            if (foods.count() == 0) {
                foods.save(Food.of("Paneer Tikka", new BigDecimal("240"), "Indian", indian));
                foods.save(Food.of("Veg Biryani", new BigDecimal("220"), "Indian", indian));
                foods.save(Food.of("Dal Makhani", new BigDecimal("210"), "Indian", indian));
                foods.save(Food.of("Butter Naan", new BigDecimal("55"), "Indian", indian));
                foods.save(Food.of("Margherita", new BigDecimal("320"), "Pizza", pizza));
                foods.save(Food.of("Farmhouse", new BigDecimal("420"), "Pizza", pizza));
                foods.save(Food.of("Pepperoni", new BigDecimal("460"), "Pizza", pizza));
                foods.save(Food.of("Garlic Bread", new BigDecimal("140"), "Pizza", pizza));
                return;
            }

            // Repair early development data created before menu items carried their restaurant relation.
            List<Food> incompleteFoods = foods.findAll().stream()
                    .filter(food -> food.restaurant == null)
                    .peek(food -> food.restaurant = "Pizza".equalsIgnoreCase(food.category) ? pizza : indian)
                    .toList();
            if (!incompleteFoods.isEmpty()) {
                foods.saveAll(incompleteFoods);
            }
            if (foods.count() < 8) {
                foods.save(Food.of("Dal Makhani", new BigDecimal("210"), "Indian", indian));
                foods.save(Food.of("Butter Naan", new BigDecimal("55"), "Indian", indian));
                foods.save(Food.of("Pepperoni", new BigDecimal("460"), "Pizza", pizza));
                foods.save(Food.of("Garlic Bread", new BigDecimal("140"), "Pizza", pizza));
            }
        };
    }
}
