package com.careslot.service;

import java.time.LocalDate;
import java.util.List;

/** Cache for provider availability; Redis when available, in-memory otherwise. */
public interface AvailabilityCache {
    List<CachedSlot> get(Long tenantId, Long providerId, LocalDate date);
    void put(Long tenantId, Long providerId, LocalDate date, List<CachedSlot> slots);
    void invalidate(Long tenantId, Long providerId, LocalDate date);
    /** Evicts all cached dates for a provider (used after bulk slot generation). */
    void invalidateProvider(Long tenantId, Long providerId);

    /** Serializable slot snapshot stored in the cache. */
    record CachedSlot(Long slotId, String startAt, String endAt, String status) {}
}
