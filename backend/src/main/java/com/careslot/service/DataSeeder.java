package com.careslot.service;

import com.careslot.domain.*;
import com.careslot.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Seeds a demo tenant ("sunrise-dental") with users, services, a provider,
 * working hours, and 14 days of slots. Runs only when app.seed.enabled=true
 * and no tenants exist. Demo logins: admin/provider/customer@careslot.demo /
 * Password123!
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final boolean enabled;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final ClinicServiceRepository services;
    private final ProviderRepository providers;
    private final WorkingHoursRepository workingHours;
    private final PasswordEncoder passwords;
    private final SlotService slotService;

    public DataSeeder(@Value("${app.seed.enabled}") boolean enabled,
                      TenantRepository tenants, AppUserRepository users,
                      ClinicServiceRepository services, ProviderRepository providers,
                      WorkingHoursRepository workingHours, PasswordEncoder passwords,
                      SlotService slotService) {
        this.enabled = enabled;
        this.tenants = tenants;
        this.users = users;
        this.services = services;
        this.providers = providers;
        this.workingHours = workingHours;
        this.passwords = passwords;
        this.slotService = slotService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || tenants.count() > 0) return;

        Tenant tenant = new Tenant();
        tenant.setName("Sunrise Dental");
        tenant.setSlug("sunrise-dental");
        tenants.save(tenant);
        Long tenantId = tenant.getId();

        AppUser admin = user(tenantId, "admin@careslot.demo", "Ava Admin", Role.TENANT_ADMIN);
        AppUser providerUser = user(tenantId, "provider@careslot.demo", "Dr. Priya Nair", Role.PROVIDER);
        user(tenantId, "customer@careslot.demo", "Sam Customer", Role.CUSTOMER);

        ClinicService cleaning = service(tenantId, "Dental Cleaning", "Routine cleaning and polish", 30, 9000);
        service(tenantId, "Root Canal", "Endodontic treatment", 90, 85000);
        service(tenantId, "Consultation", "New patient consultation", 30, 5000);

        Provider provider = new Provider();
        provider.setTenantId(tenantId);
        provider.setUserId(providerUser.getId());
        provider.setName("Dr. Priya Nair");
        provider.setSpecialty("General Dentistry");
        providers.save(provider);

        for (int dow = 1; dow <= 5; dow++) {
            slotService.upsertHours(tenantId, provider.getId(), dow,
                    LocalTime.of(9, 0), LocalTime.of(17, 0));
        }

        LocalDate today = LocalDate.now();
        int generated = slotService.generateSlots(tenantId, provider.getId(),
                today, today.plusDays(13), 30);
        log.info("Seeded demo tenant sunrise-dental: admin={}, generated {} slots",
                admin.getEmail(), generated);
    }

    private AppUser user(Long tenantId, String email, String name, Role role) {
        AppUser u = new AppUser();
        u.setTenantId(tenantId);
        u.setEmail(email);
        u.setFullName(name);
        u.setPasswordHash(passwords.encode("Password123!"));
        u.setRole(role);
        return users.save(u);
    }

    private ClinicService service(Long tenantId, String name, String desc, int mins, int cents) {
        ClinicService s = new ClinicService();
        s.setTenantId(tenantId);
        s.setName(name);
        s.setDescription(desc);
        s.setDurationMinutes(mins);
        s.setPriceCents(cents);
        s.setActive(true);
        return services.save(s);
    }
}
