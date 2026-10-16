package com.careslot.web;

import com.careslot.domain.*;
import com.careslot.repo.*;
import com.careslot.security.TenantPrincipal;
import com.careslot.service.BookingService;
import com.careslot.service.BookingService.AppointmentView;
import com.careslot.service.SlotService;
import com.careslot.tenant.TenantContext;
import com.careslot.web.dto.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class AdminController {

    private final ClinicServiceRepository services;
    private final ProviderRepository providers;
    private final AppUserRepository users;
    private final WorkingHoursRepository workingHours;
    private final SlotService slots;
    private final BookingService bookings;
    private final PasswordEncoder passwords;

    public AdminController(ClinicServiceRepository services, ProviderRepository providers,
                           AppUserRepository users, WorkingHoursRepository workingHours,
                           SlotService slots, BookingService bookings, PasswordEncoder passwords) {
        this.services = services;
        this.providers = providers;
        this.users = users;
        this.workingHours = workingHours;
        this.slots = slots;
        this.bookings = bookings;
        this.passwords = passwords;
    }

    // ---- services ----
    @GetMapping("/services")
    public List<ServiceDto> listServices() {
        return services.findByTenantId(TenantContext.require()).stream().map(this::toDto).toList();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ServiceDto createService(@Valid @RequestBody UpsertServiceRequest req) {
        ClinicService s = new ClinicService();
        s.setTenantId(TenantContext.require());
        s.setName(req.name());
        s.setDescription(req.description());
        s.setDurationMinutes(req.durationMinutes());
        s.setPriceCents(req.priceCents());
        s.setActive(req.active() == null || req.active());
        return toDto(services.save(s));
    }

    @PutMapping("/services/{id}")
    @Transactional
    public ServiceDto updateService(@PathVariable Long id, @Valid @RequestBody UpsertServiceRequest req) {
        ClinicService s = services.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new ApiException.NotFound("Service not found"));
        s.setName(req.name());
        s.setDescription(req.description());
        s.setDurationMinutes(req.durationMinutes());
        s.setPriceCents(req.priceCents());
        if (req.active() != null) s.setActive(req.active());
        return toDto(services.save(s));
    }

    @DeleteMapping("/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteService(@PathVariable Long id) {
        ClinicService s = services.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new ApiException.NotFound("Service not found"));
        s.setActive(false);
        services.save(s);
    }

    // ---- providers ----
    @GetMapping("/providers")
    public List<ProviderDto> listProviders() {
        return providers.findByTenantId(TenantContext.require()).stream()
                .map(p -> new ProviderDto(p.getId(), p.getName(), p.getSpecialty(), p.isActive()))
                .toList();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ProviderDto createProvider(@Valid @RequestBody CreateProviderRequest req,
                                      @AuthenticationPrincipal TenantPrincipal principal) {
        Long tenantId = principal.tenantId();
        if (users.existsByTenantIdAndEmail(tenantId, req.email().toLowerCase())) {
            throw new ApiException.Conflict("Email already registered for this clinic");
        }
        AppUser user = new AppUser();
        user.setTenantId(tenantId);
        user.setEmail(req.email().toLowerCase());
        user.setFullName(req.name());
        user.setPasswordHash(passwords.encode(req.password()));
        user.setRole(Role.PROVIDER);
        users.save(user);

        Provider p = new Provider();
        p.setTenantId(tenantId);
        p.setUserId(user.getId());
        p.setName(req.name());
        p.setSpecialty(req.specialty());
        providers.save(p);
        return new ProviderDto(p.getId(), p.getName(), p.getSpecialty(), p.isActive());
    }

    // ---- appointments ----
    @GetMapping("/appointments")
    public List<AppointmentView> appointments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal TenantPrincipal principal) {
        return bookings.tenantDay(principal.tenantId(), date).stream()
                .map(a -> bookings.view(principal.tenantId(), a))
                .toList();
    }

    @PostMapping("/providers/{id}/slots/generate")
    public Map<String, Integer> generateFor(@PathVariable Long id,
                                            @Valid @RequestBody GenerateSlotsRequest req,
                                            @AuthenticationPrincipal TenantPrincipal principal) {
        providers.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new ApiException.NotFound("Provider not found"));
        int n = slots.generateSlots(principal.tenantId(), id,
                LocalDate.parse(req.fromDate()), LocalDate.parse(req.toDate()),
                req.slotMinutes() == null ? 30 : req.slotMinutes());
        return Map.of("generated", n);
    }

    @PostMapping("/providers/{id}/schedule")
    public WorkingHoursDto setHours(@PathVariable Long id,
                                    @Valid @RequestBody UpsertHoursRequest req,
                                    @AuthenticationPrincipal TenantPrincipal principal) {
        providers.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new ApiException.NotFound("Provider not found"));
        WorkingHours w = slots.upsertHours(principal.tenantId(), id, req.dayOfWeek(),
                LocalTime.parse(req.startTime()), LocalTime.parse(req.endTime()));
        return new WorkingHoursDto(w.getDayOfWeek(), w.getStartTime().toString(), w.getEndTime().toString());
    }

    private ServiceDto toDto(ClinicService s) {
        return new ServiceDto(s.getId(), s.getName(), s.getDescription(),
                s.getDurationMinutes(), s.getPriceCents(), s.isActive());
    }
}
