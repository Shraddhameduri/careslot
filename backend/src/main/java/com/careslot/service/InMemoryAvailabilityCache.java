package com.careslot.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory availability cache with 60s TTL; used when Redis is absent. */
@Component
public class InMemoryAvailabilityCache implements AvailabilityCache {

    private record Entry(List<CachedSlot> slots, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    private String key(Long tenantId, Long providerId, LocalDate date) {
        return tenantId + ":" + providerId + ":" + date;
    }

    @Override
    public List<CachedSlot> get(Long tenantId, Long providerId, LocalDate date) {
        Entry e = store.get(key(tenantId, providerId, date));
        if (e == null || e.expiresAt().isBefore(Instant.now())) {
            store.remove(key(tenantId, providerId, date));
            return null;
        }
        return e.slots();
    }

    @Override
    public void put(Long tenantId, Long providerId, LocalDate date, List<CachedSlot> slots) {
        store.put(key(tenantId, providerId, date),
                new Entry(slots, Instant.now().plusSeconds(60)));
    }

    @Override
    public void invalidate(Long tenantId, Long providerId, LocalDate date) {
        store.remove(key(tenantId, providerId, date));
    }

    @Override
    public void invalidateProvider(Long tenantId, Long providerId) {
        String prefix = tenantId + ":" + providerId + ":";
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
