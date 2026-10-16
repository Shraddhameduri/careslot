package com.careslot.service;

import com.careslot.domain.TimeSlot;
import com.careslot.repo.TimeSlotRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Reads availability through the cache, falling back to the database. */
@Service
public class AvailabilityService {

    private final TimeSlotRepository slots;
    private final AvailabilityCache cache;

    public AvailabilityService(TimeSlotRepository slots, AvailabilityCache cache) {
        this.slots = slots;
        this.cache = cache;
    }

    public List<AvailabilityCache.CachedSlot> availability(Long tenantId, Long providerId, LocalDate date) {
        List<AvailabilityCache.CachedSlot> cached = cache.get(tenantId, providerId, date);
        if (cached != null) return cached;

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        List<TimeSlot> rows = slots.findByTenantIdAndProviderIdAndStartAtBetweenOrderByStartAt(
                tenantId, providerId, from, to);
        List<AvailabilityCache.CachedSlot> result = rows.stream()
                .map(s -> new AvailabilityCache.CachedSlot(
                        s.getId(), s.getStartAt().toString(), s.getEndAt().toString(),
                        s.getStatus().name()))
                .toList();
        cache.put(tenantId, providerId, date, result);
        return result;
    }

    /** Called after any slot mutation: evicts the cache and pushes an SSE event. */
    public void slotChanged(TimeSlot slot, SseService sse) {
        cache.invalidate(slot.getTenantId(), slot.getProviderId(), slot.getStartAt().toLocalDate());
        sse.publish(slot.getTenantId(), slot.getProviderId(), slot.getStartAt().toLocalDate(),
                slot.getId(), slot.getStatus().name());
    }

    /** Evicts all cached availability dates for a provider. */
    public void invalidateProvider(Long tenantId, Long providerId) {
        cache.invalidateProvider(tenantId, providerId);
    }
}
