package com.foodordering.food;

import com.foodordering.security.SecuritySupport;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RefreshScope
public class FoodController {
    private static final Logger log = LoggerFactory.getLogger(FoodController.class);
    private final FoodService service;
    private final StringRedisTemplate redis;
    @Value("${app.welcome-message}") String welcome;
    FoodController(FoodService service, StringRedisTemplate redis) { this.service = service; this.redis = redis; }

    @GetMapping("/foods/info") Map<String, String> info() { return Map.of("message", welcome); }
    @GetMapping("/foods/{id}") FoodResponse get(@PathVariable Long id, HttpServletResponse response) {
        long started = System.nanoTime(); boolean hit = Boolean.TRUE.equals(redis.hasKey("foods::" + id));
        response.setHeader("X-Cache", hit ? "HIT" : "MISS"); log.info("CACHE {} - food {}", hit ? "HIT" : "MISS", id);
        try { return service.get(id); } finally { log.info("Food {} served in {} ms", id, (System.nanoTime() - started) / 1_000_000); }
    }
    @GetMapping("/foods") List<FoodResponse> list(@RequestParam(required = false) String category, @RequestParam(required = false) Long restaurantId, @RequestParam(defaultValue = "false") boolean all, Authentication authentication) {
        if (all && !"ADMIN".equals(SecuritySupport.principal(authentication).role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can view unavailable foods");
        }
        return service.list(category, restaurantId, all);
    }
    @PostMapping("/foods") @ResponseStatus(HttpStatus.CREATED) FoodResponse create(@Valid @RequestBody FoodRequest request) { return service.create(request); }
    @PutMapping("/foods/{id}") FoodResponse update(@PathVariable Long id, @Valid @RequestBody FoodRequest request) { return service.update(id, request); }
    @DeleteMapping("/foods/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable Long id) { service.delete(id); }
    @PostMapping("/restaurants") @ResponseStatus(HttpStatus.CREATED) RestaurantResponse restaurant(@Valid @RequestBody RestaurantRequest request) { return service.restaurant(request); }
    @GetMapping("/restaurants") List<RestaurantResponse> restaurants() { return service.restaurants(); }
}
