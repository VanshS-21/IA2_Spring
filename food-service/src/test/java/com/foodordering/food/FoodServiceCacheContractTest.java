package com.foodordering.food;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

class FoodServiceCacheContractTest {
    @Test
    void foodReadsWritesAndDeletesUseTheSameRedisCacheKey() throws Exception {
        Cacheable read = FoodService.class.getDeclaredMethod("get", Long.class).getAnnotation(Cacheable.class);
        CachePut write = FoodService.class.getDeclaredMethod("update", Long.class, FoodRequest.class).getAnnotation(CachePut.class);
        CacheEvict delete = FoodService.class.getDeclaredMethod("delete", Long.class).getAnnotation(CacheEvict.class);

        assertThat(read.value()).containsExactly("foods");
        assertThat(read.key()).isEqualTo("#id");
        assertThat(write.value()).containsExactly("foods");
        assertThat(write.key()).isEqualTo("#id");
        assertThat(delete.value()).containsExactly("foods");
        assertThat(delete.key()).isEqualTo("#id");
    }
}
