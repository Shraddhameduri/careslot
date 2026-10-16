package com.careslot.service;

import com.careslot.domain.*;
import com.careslot.repo.*;
import com.careslot.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Provider schedule management and slot generation from working hours. */
@Service
public class SlotService {

    private final WorkingHoursRepository workingHours;
    private final TimeSlotRepository slots;
    private final ProviderRepository providers;
    private final AvailabilityService availability;
    private final SseService sse;

    public SlotService(WorkingHoursRepository workingHours, TimeSlotRepository slots,
                       ProviderRepository providers, AvailabilityService availability,
                       SseService sse) {
        this.workingHours = workingHours;
        this.slots = slots;
        this.providers = providers;
        this.availability = availability;
        this.sse = sse;
    }

    @Transactional
    public WorkingHours upsertHours(Long tenantId, Long providerId, int dayOfWeek,
                                    LocalTime start, LocalTime end) {
        if (dayOfWeek < 1 || dayOfWeek > 7) throw new ApiException.BadRequest("dayOfWeek must be 1-7");
        if (!start.isBefore(end)) throw new ApiException.BadRequest("startTime must be before endTime");
        assertProvider(tenantId, providerId);
        WorkingHours wh = workingHours
                .findByTenantIdAndProviderIdAndDayOfWeek(tenantId, providerId, dayOfWeek)
                .orElseGet(() -> {
                    WorkingHours w = new WorkingHours();
                    w.setTenantId(tenantId);
                    w.setProviderId(providerId);
                    w.setDayOfWeek(dayOfWeek);
                    return w;
                });
        wh.setStartTime(start);
        wh.setEndTime(end);
        return workingHours.save(wh);
    }

    @Transactional(readOnly = true)
    public List<WorkingHours> myHours(Long tenantId, Long providerId) {
        assertProvider(tenantId, providerId);
        return workingHours.findByTenantIdAndProviderId(tenantId, providerId);
    }

    /**
     * Generates AVAILABLE slots for a provider across a date range from their
     * working hours. Idempotent: existing slots are skipped (also enforced by
     * the uq_time_slots_provider_start unique constraint).
     */
    @Transactional
    public int generateSlots(Long tenantId, Long providerId, LocalDate from, LocalDate to, int slotMinutes) {
        if (slotMinutes <= 0 || slotMinutes > 240) throw new ApiException.BadRequest("slotMinutes must be 1-240");
        if (to.isBefore(from)) throw new ApiException.BadRequest("toDate must be on/after fromDate");
        if (from.plusDays(62).isBefore(to)) throw new ApiException.BadRequest("Range limited to 62 days");
        assertProvider(tenantId, providerId);

        List<WorkingHours> hours = workingHours.findByTenantIdAndProviderId(tenantId, providerId);
        int created = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int dow = date.getDayOfWeek().getValue();
            for (WorkingHours wh : hours) {
                if (wh.getDayOfWeek() != dow) continue;
                LocalDateTime cursor = LocalDateTime.of(date, wh.getStartTime());
                LocalDateTime end = LocalDateTime.of(date, wh.getEndTime());
                while (!cursor.plusMinutes(slotMinutes).isAfter(end)) {
                    if (!slots.existsByTenantIdAndProviderIdAndStartAt(tenantId, providerId, cursor)) {
                        TimeSlot slot = new TimeSlot();
                        slot.setTenantId(tenantId);
                        slot.setProviderId(providerId);
                        slot.setStartAt(cursor);
                        slot.setEndAt(cursor.plusMinutes(slotMinutes));
                        slot.setStatus(SlotStatus.AVAILABLE);
                        slots.save(slot);
                        created++;
                    }
                    cursor = cursor.plusMinutes(slotMinutes);
                }
            }
        }
        availability.invalidateProvider(tenantId, providerId);
        return created;
    }

    private void assertProvider(Long tenantId, Long providerId) {
        providers.findByIdAndTenantId(providerId, tenantId)
                .orElseThrow(() -> new ApiException.NotFound("Provider not found"));
    }
}
