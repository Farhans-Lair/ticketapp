package com.ticketapp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * CacheConfig — in-process Caffeine cache for high-traffic read paths.
 *
 * Cache names and TTLs:
 *  ┌─────────────────────┬──────────┬──────────────────────────────────────┐
 *  │ Name                │ TTL      │ What is cached                       │
 *  ├─────────────────────┼──────────┼──────────────────────────────────────┤
 *  │ publishedEvents     │ 30 s     │ All published events by category     │
 *  │ featuredEvents      │ 60 s     │ Featured event list                  │
 *  │ trendingEvents      │ 120 s    │ Trending events (7-day window)       │
 *  │ eventCategories     │ 300 s    │ Admin-managed category list          │
 *  └─────────────────────┴──────────┴──────────────────────────────────────┘
 *
 * Cache invalidation: @CacheEvict annotations in EventService clear the
 * relevant caches when events are created, updated, approved, or rejected.
 *
 * Scope: in-process only. All ASG instances maintain independent caches.
 * For cross-instance consistency, replace Caffeine with Spring Cache +
 * Redis (spring-boot-starter-cache + spring-boot-starter-data-redis).
 * For this project's scale (1–3 instances) a 30 s stale window is fine.
 */
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
