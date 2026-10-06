package com.foodordering.food;
import jakarta.validation.constraints.*; import java.io.Serializable; import java.math.BigDecimal;
record FoodRequest(@NotBlank String foodName,@NotNull @Positive BigDecimal price,@NotBlank String category,@NotNull Long restaurantId,Boolean availability){} record RestaurantRequest(@NotBlank String restaurantName,@NotBlank String location){} record RestaurantResponse(Long restaurantId,String restaurantName,String location){}
