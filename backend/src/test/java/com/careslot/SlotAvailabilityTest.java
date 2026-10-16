package com.careslot;

import com.careslot.domain.*;
import com.careslot.service.AvailabilityService;
import com.careslot.service.BookingService;
import com.careslot.service.SlotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Slot generation + availability cache (in-memory fallback; no Redis in tests). */
@Transactional
class SlotAvailabilityTest extends BaseIT {

    @Autowired SlotService slotService;
    @Autowired AvailabilityService availability;
    @Autowired BookingService bookingService;

    @Test
    void availabilityReflectsBooking_cacheInvalidated() {
        String suffix = String.valueOf(System.nanoTime());
        AppUser pu = new AppUser();
        pu.setTenantId(tenantA);
        pu.setEmail("pav" + suffix + "@t.co");
        pu.setFullName("P Av");
        pu.setPasswordHash("x");
        pu.setRole(Role.PROVIDER);
        users.save(pu);
        Provider p = new Provider();
        p.setTenantId(tenantA);
        p.setUserId(pu.getId());
        p.setName("P Av");
        providers.save(p);

        ClinicService s = new ClinicService();
        s.setTenantId(tenantA);
        s.setName("Svc " + suffix);
        s.setDurationMinutes(30);
        s.setPriceCents(1000);
        s.setActive(true);
        services.save(s);

        LocalDate day = LocalDate.now().plusDays(5);
        slotService.upsertHours(tenantA, p.getId(), day.getDayOfWeek().getValue(),
                LocalTime.of(10, 0), LocalTime.of(11, 0));
        slotService.generateSlots(tenantA, p.getId(), day, day, 30);

        var before = availability.availability(tenantA, p.getId(), day);
        assertThat(before).hasSize(2);
        assertThat(before).allMatch(c -> c.status().equals("AVAILABLE"));

        // Second read comes from cache (would throw if the cache path were broken).
        var cached = availability.availability(tenantA, p.getId(), day);
        assertThat(cached).hasSize(2);

        bookingService.book(tenantA, customerA.user().getId(), before.get(0).slotId(), s.getId(), null);

        var after = availability.availability(tenantA, p.getId(), day);
        assertThat(after.stream().filter(c -> c.status().equals("AVAILABLE")).count()).isEqualTo(1);
        assertThat(after.stream().filter(c -> c.status().equals("BOOKED")).count()).isEqualTo(1);
    }
}
