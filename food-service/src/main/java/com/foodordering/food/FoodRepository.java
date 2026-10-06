package com.foodordering.food;
import org.springframework.data.jpa.repository.JpaRepository;
interface FoodRepository extends JpaRepository<Food,Long>{} interface RestaurantRepository extends JpaRepository<Restaurant,Long>{}
