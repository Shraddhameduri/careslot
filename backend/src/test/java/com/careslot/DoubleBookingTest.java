package com.careslot;

import com.careslot.domain.*;
import com.careslot.repo.PaymentIntentRepository;
import com.careslot.service.BookingService;
import com.careslot.service.SlotService;
import com.careslot.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves double-booking prevention:
 * 1. Concurrent bookings of one slot -> exactly one wins, the other gets 409.
 * 2. Sequential second booking -> 409.
 * 3. DB unique constraint on appointments.slot_id is the final backstop.
 */
class DoubleBookingTest extends BaseIT {

    @Autowired BookingService bookingService;
    @Autowired SlotService slotService;
    @Autowired PaymentIntentRepository paymentIntents;

    record Fixture(Long providerId, Long serviceId, Long slotId, LocalDate date) {}

    private Fixture fixture() {
        String suffix = String.valueOf(System.nanoTime());
        AppUser pu = new AppUser();
        pu.setTenantId(tenantA);
        pu.setEmail("prov" + suffix + "@t.co");
        pu.setFullName("Prov " + suffix);
        pu.setPasswordHash("x");
        pu.setRole(Role.PROVIDER);
        users.save(pu);

        Provider p = new Provider();
        p.setTenantId(tenantA);
        p.setUserId(pu.getId());
        p.setName("Prov " + suffix);
        providers.save(p);

        ClinicService s = new ClinicService();
        s.setTenantId(tenantA);
        s.setName("Checkup " + suffix);
        s.setDurationMinutes(30);
        s.setPriceCents(5000);
        s.setActive(true);
        services.save(s);

        LocalDate day = LocalDate.now().plusDays(7);
        slotService.upsertHours(tenantA, p.getId(), day.getDayOfWeek().getValue(),
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        int generated = slotService.generateSlots(tenantA, p.getId(), day, day, 30);
        assertThat(generated).isEqualTo(6);

        // Idempotent: regenerating yields zero new slots.
        assertThat(slotService.generateSlots(tenantA, p.getId(), day, day, 30)).isZero();

        TimeSlot slot = slots.findByTenantIdAndProviderIdAndStartAtBetweenOrderByStartAt(
                tenantA, p.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay()).get(0);
        return new Fixture(p.getId(), s.getId(), slot.getId(), day);
    }

    private AppUser newCustomer() {
        String suffix = String.valueOf(System.nanoTime());
        AppUser c = new AppUser();
        c.setTenantId(tenantA);
        c.setEmail("c" + suffix + "@t.co");
        c.setFullName("C " + suffix);
        c.setPasswordHash("x");
        c.setRole(Role.CUSTOMER);
        return users.save(c);
    }

    @Test
    void concurrentBookingsOfSameSlot_exactlyOneSucceeds() throws Exception {
        Fixture f = fixture();
        AppUser c1 = newCustomer();
        AppUser c2 = newCustomer();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (AppUser c : List.of(c1, c2)) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) return "TIMEOUT";
                try {
                    bookingService.book(tenantA, c.getId(), f.slotId(), f.serviceId(), null);
                    return "BOOKED_OK";
                } catch (ApiException.Conflict e) {
                    return "CONFLICT";
                }
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        List<String> results = new ArrayList<>();
        for (Future<String> fut : futures) results.add(fut.get(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertThat(results).containsExactlyInAnyOrder("BOOKED_OK", "CONFLICT");
        assertThat(appointments.findAll().stream()
                .filter(a -> a.getSlotId().equals(f.slotId())).count()).isEqualTo(1);

        // cleanup for other tests (payment intents reference the appointment)
        appointments.findAll().stream()
                .filter(a -> a.getSlotId().equals(f.slotId()))
                .forEach(a -> {
                    paymentIntents.findAll().stream()
                            .filter(pi -> a.getId().equals(pi.getAppointmentId()))
                            .forEach(paymentIntents::delete);
                    appointments.delete(a);
                });
        slots.findById(f.slotId()).ifPresent(s -> {
            s.setStatus(SlotStatus.AVAILABLE);
            slots.save(s);
        });
    }

    @Test
    void sequentialDoubleBooking_secondGets409() throws Exception {
        Fixture f = fixture();
        var r1 = auth.register(tenantA, "s1" + System.nanoTime() + "@t.co", "S1", "Password123!");
        var r2 = auth.register(tenantA, "s2" + System.nanoTime() + "@t.co", "S2", "Password123!");

        postJson("/api/v1/bookings",
                Map.of("slotId", f.slotId(), "serviceId", f.serviceId()),
                r1.tokens().accessToken(), tenantA)
                .andExpect(status().isCreated());

        postJson("/api/v1/bookings",
                Map.of("slotId", f.slotId(), "serviceId", f.serviceId()),
                r2.tokens().accessToken(), tenantA)
                .andExpect(status().isConflict());
    }

    @Test
    void dbUniqueConstraint_backstopsDoubleBooking() {
        Fixture f = fixture();
        AppUser c1 = newCustomer();
        AppUser c2 = newCustomer();

        Appointment a1 = new Appointment();
        a1.setTenantId(tenantA);
        a1.setSlotId(f.slotId());
        a1.setCustomerId(c1.getId());
        a1.setServiceId(f.serviceId());
        a1.setStatus(AppointmentStatus.CONFIRMED);
        appointments.saveAndFlush(a1);

        Appointment a2 = new Appointment();
        a2.setTenantId(tenantA);
        a2.setSlotId(f.slotId());
        a2.setCustomerId(c2.getId());
        a2.setServiceId(f.serviceId());
        a2.setStatus(AppointmentStatus.CONFIRMED);
        assertThatThrownBy(() -> appointments.saveAndFlush(a2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
