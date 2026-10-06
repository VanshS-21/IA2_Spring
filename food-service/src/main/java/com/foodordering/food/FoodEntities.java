package com.foodordering.food;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity class Restaurant {@Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long restaurantId; String restaurantName; String location; static Restaurant of(String n,String l){Restaurant r=new Restaurant();r.restaurantName=n;r.location=l;return r;}}
@Entity class Food {@Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long foodId; String foodName; BigDecimal price; String category; boolean availability=true; @ManyToOne(fetch=FetchType.EAGER) Restaurant restaurant; static Food of(String n,BigDecimal p,String c,Restaurant r){Food f=new Food();f.foodName=n;f.price=p;f.category=c;f.restaurant=r;return f;}}
