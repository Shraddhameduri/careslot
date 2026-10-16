package com.careslot.web;

import com.careslot.domain.WorkingHours;
import com.careslot.repo.ProviderRepository;
import com.careslot.security.TenantPrincipal;
import com.careslot.service.BookingService;
import com.careslot.service.BookingService.AppointmentView;
import com.careslot.service.SlotService;
import com.careslot.web.dto.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/provider")
@PreAuthorize("hasAnyRole('PROVIDER','TENANT_ADMIN')")
public class ProviderController {

    private final SlotService slots;
    private final BookingService bookings;
    private final ProviderRepository providers;

    public ProviderController(SlotService slots, BookingService bookings, ProviderRepository providers) {
        this.slots = slots;
        this.bookings = bookings;
        this.providers = providers;
    }

    private Long providerId(TenantPrincipal p) {
        return providers.findByTenantIdAndUserId(p.tenantId(), p.userId())
                .orElseThrow(() -> new ApiException.NotFound("Provider profile not found")).getId();
    }

    @GetMapping("/schedule")
    public List<WorkingHoursDto> schedule(@AuthenticationPrincipal TenantPrincipal principal) {
        return slots.myHours(principal.tenantId(), providerId(principal)).stream()
                .map(w -> new WorkingHoursDto(w.getDayOfWeek(),
                        w.getStartTime().toString(), w.getEndTime().toString()))
                .toList();
    }

    @PutMapping("/schedule")
    public WorkingHoursDto upsertHours(@Valid @RequestBody UpsertHoursRequest req,
                                       @AuthenticationPrincipal TenantPrincipal principal) {
        WorkingHours w = slots.upsertHours(principal.tenantId(), providerId(principal),
                req.dayOfWeek(), LocalTime.parse(req.startTime()), LocalTime.parse(req.endTime()));
        return new WorkingHoursDto(w.getDayOfWeek(), w.getStartTime().toString(), w.getEndTime().toString());
    }

    @PostMapping("/slots/generate")
    public Map<String, Integer> generate(@Valid @RequestBody GenerateSlotsRequest req,
                                         @AuthenticationPrincipal TenantPrincipal principal) {
        int n = slots.generateSlots(principal.tenantId(), providerId(principal),
                LocalDate.parse(req.fromDate()), LocalDate.parse(req.toDate()),
                req.slotMinutes() == null ? 30 : req.slotMinutes());
        return Map.of("generated", n);
    }

    @GetMapping("/appointments")
    public List<AppointmentView> appointments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal TenantPrincipal principal) {
        return bookings.providerDay(principal.tenantId(), principal.userId(), date).stream()
                .map(a -> bookings.view(principal.tenantId(), a))
                .toList();
    }
}
