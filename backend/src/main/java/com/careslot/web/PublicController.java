package com.careslot.web;

import com.careslot.repo.*;
import com.careslot.service.AvailabilityService;
import com.careslot.service.SseService;
import com.careslot.tenant.TenantContext;
import com.careslot.web.dto.Dtos.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;

/** Public (unauthenticated) endpoints; tenant resolved from ?tenant=slug or X-Tenant-Id. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    private final TenantRepository tenants;
    private final ClinicServiceRepository services;
    private final ProviderRepository providers;
    private final AvailabilityService availability;
    private final SseService sse;

    public PublicController(TenantRepository tenants, ClinicServiceRepository services,
                            ProviderRepository providers, AvailabilityService availability,
                            SseService sse) {
        this.tenants = tenants;
        this.services = services;
        this.providers = providers;
        this.availability = availability;
        this.sse = sse;
    }

    @GetMapping("/tenants/{slug}")
    public TenantDto tenant(@PathVariable String slug) {
        var t = tenants.findBySlug(slug)
                .orElseThrow(() -> new ApiException.NotFound("Clinic not found"));
        return new TenantDto(t.getId(), t.getName(), t.getSlug());
    }

    @GetMapping("/services")
    public List<ServiceDto> services() {
        Long tenantId = TenantContext.require();
        return services.findByTenantIdAndActiveTrue(tenantId).stream()
                .map(s -> new ServiceDto(s.getId(), s.getName(), s.getDescription(),
                        s.getDurationMinutes(), s.getPriceCents(), s.isActive()))
                .toList();
    }

    @GetMapping("/providers")
    public List<ProviderDto> providers() {
        Long tenantId = TenantContext.require();
        return providers.findByTenantIdAndActiveTrue(tenantId).stream()
                .map(p -> new ProviderDto(p.getId(), p.getName(), p.getSpecialty(), p.isActive()))
                .toList();
    }

    @GetMapping("/availability")
    public List<SlotDto> availability(
            @RequestParam Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long tenantId = TenantContext.require();
        return availability.availability(tenantId, providerId, date).stream()
                .map(c -> new SlotDto(c.slotId(), c.startAt(), c.endAt(), c.status(), null))
                .toList();
    }

    @GetMapping(value = "/availability/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long tenantId = TenantContext.require();
        return sse.subscribe(tenantId, providerId, date);
    }
}
