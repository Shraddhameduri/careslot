package com.careslot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Redis-backed availability cache. If Redis is unreachable at startup the bean
 * fails its health probe and Spring falls back to {@link InMemoryAvailabilityCache},
 * so the app (and tests) run fine without Redis.
 */
@Component
@Primary
public class RedisAvailabilityCache implements AvailabilityCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAvailabilityCache.class);
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final InMemoryAvailabilityCache fallback;
    private volatile boolean redisUp = true;

    public RedisAvailabilityCache(StringRedisTemplate redis, ObjectMapper mapper,
                                  InMemoryAvailabilityCache fallback) {
        this.redis = redis;
        this.mapper = mapper;
        this.fallback = fallback;
        try {
            redis.getConnectionFactory().getConnection().ping();
            log.info("Redis reachable: availability cache backed by Redis");
        } catch (Exception e) {
            redisUp = false;
            log.warn("Redis unreachable ({}); using in-memory availability cache fallback", e.getMessage());
        }
    }

    public boolean isRedisUp() {
        return redisUp;
    }

    private String key(Long tenantId, Long providerId, LocalDate date) {
        return "avail:%d:%d:%s".formatted(tenantId, providerId, date);
    }

    @Override
    public List<CachedSlot> get(Long tenantId, Long providerId, LocalDate date) {
        if (!redisUp) return fallback.get(tenantId, providerId, date);
        try {
            String json = redis.opsForValue().get(key(tenantId, providerId, date));
            if (json == null) return null;
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            redisUp = false;
            log.warn("Redis read failed, falling back to in-memory cache: {}", e.getMessage());
            return fallback.get(tenantId, providerId, date);
        }
    }

    @Override
    public void put(Long tenantId, Long providerId, LocalDate date, List<CachedSlot> slots) {
        if (!redisUp) {
            fallback.put(tenantId, providerId, date, slots);
            return;
        }
        try {
            redis.opsForValue().set(key(tenantId, providerId, date), mapper.writeValueAsString(slots), TTL);
        } catch (Exception e) {
            redisUp = false;
            log.warn("Redis write failed, falling back to in-memory cache: {}", e.getMessage());
            fallback.put(tenantId, providerId, date, slots);
        }
    }

    @Override
    public void invalidate(Long tenantId, Long providerId, LocalDate date) {
        fallback.invalidate(tenantId, providerId, date);
        if (!redisUp) return;
        try {
            redis.delete(key(tenantId, providerId, date));
        } catch (Exception e) {
            redisUp = false;
            log.warn("Redis delete failed: {}", e.getMessage());
        }
    }

    @Override
    public void invalidateProvider(Long tenantId, Long providerId) {
        fallback.invalidateProvider(tenantId, providerId);
        if (!redisUp) return;
        try {
            var keys = redis.keys("avail:%d:%d:*".formatted(tenantId, providerId));
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception e) {
            redisUp = false;
            log.warn("Redis keys/delete failed: {}", e.getMessage());
        }
    }
}
