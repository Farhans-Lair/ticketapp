package com.ticketapp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/* CacheConfig — in-process Caffeine cache for high-traffic read paths. */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // Default spec — overridden per-cache below for different TTLs
        manager.setCaffeine(defaultSpec());
        // Register named caches with custom TTLs
        manager.registerCustomCache("publishedEvents",
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).maximumSize(200).build());
        manager.registerCustomCache("featuredEvents",
                Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.SECONDS).maximumSize(50).build());
        manager.registerCustomCache("trendingEvents",
                Caffeine.newBuilder().expireAfterWrite(120, TimeUnit.SECONDS).maximumSize(50).build());
        manager.registerCustomCache("eventCategories",
                Caffeine.newBuilder().expireAfterWrite(300, TimeUnit.SECONDS).maximumSize(50).build());
        return manager;
    }

    private Caffeine<Object, Object> defaultSpec() {
        return Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(500);
    }
}
