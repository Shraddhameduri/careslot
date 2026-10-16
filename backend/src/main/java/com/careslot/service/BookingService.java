package com.careslot.service;

import com.careslot.domain.*;
import com.careslot.payment.PaymentService;
import com.careslot.repo.*;
import com.careslot.security.TenantPrincipal;
import com.careslot.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Double-booking prevention, two layers:
 * 1. Transactional: the slot row is locked with SELECT ... FOR UPDATE and its
 *    status re-checked inside the same transaction, so two concurrent bookings
 *    of one slot serialize and exactly one wins.
 * 2. Database backstop: UNIQUE(appointments.slot_id) plus
 *    UNIQUE(time_slots.tenant_id, provider_id, start_at) — any race that slips
 *    past the lock fails the insert and is mapped to HTTP 409.
 */
@Service
public class BookingService {

    private final TimeSlotRepository slots;
    private final AppointmentRepository appointments;
    private final ClinicServiceRepository services;
    private final ProviderRepository providers;
    private final AppUserRepository users;
    private final PaymentService payments;
    private final AvailabilityService availability;
    private final SseService sse;

    public BookingService(TimeSlotRepository slots, AppointmentRepository appointments,
                          ClinicServiceRepository services, ProviderRepository providers,
                          AppUserRepository users, PaymentService payments,
                          AvailabilityService availability, SseService sse) {
        this.slots = slots;
        this.appointments = appointments;
        this.services = services;
        this.providers = providers;
        this.users = users;
        this.payments = payments;
        this.availability = availability;
        this.sse = sse;
    }

    @Transactional
    public Appointment book(Long tenantId, Long customerId, Long slotId, Long serviceId, String notes) {
        TimeSlot slot = slots.findByIdAndTenantIdForUpdate(slotId, tenantId)
                .orElseThrow(() -> new ApiException.NotFound("Time slot not found"));
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new ApiException.Conflict("Time slot is no longer available");
        }
        ClinicService service = services.findByIdAndTenantId(serviceId, tenantId)
                .filter(ClinicService::isActive)
                .orElseThrow(() -> new ApiException.NotFound("Service not found"));

        // Mock payment: create + confirm a Stripe-style intent before booking.
        var intent = payments.createIntent(tenantId, service.getPriceCents(), "USD");
        var confirmed = payments.confirmIntent(tenantId, intent.getProviderRef(), true);
        if (confirmed.getStatus() != PaymentStatus.CONFIRMED) {
            throw new ApiException.BadRequest("Payment failed");
        }

        Appointment appt = new Appointment();
        appt.setTenantId(tenantId);
        appt.setSlotId(slot.getId());
        appt.setCustomerId(customerId);
        appt.setServiceId(service.getId());
        appt.setStatus(AppointmentStatus.CONFIRMED);
        appt.setPaymentRef(intent.getProviderRef());
        appt.setNotes(notes);
        try {
            appointments.saveAndFlush(appt);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException.Conflict("Time slot was just booked by someone else");
        }
        slot.setStatus(SlotStatus.BOOKED);
        slots.save(slot);
        confirmed.setAppointmentId(appt.getId());

        availability.slotChanged(slot, sse);
        return appt;
    }

    @Transactional
    public Appointment cancel(Long tenantId, TenantPrincipal principal, Long appointmentId) {
        Appointment appt = appointments.findByIdAndTenantId(appointmentId, tenantId)
                .orElseThrow(() -> new ApiException.NotFound("Appointment not found"));
        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ApiException.BadRequest("Appointment already cancelled");
        }
        // Ownership check: the customer who booked, the provider of the slot,
        // or a tenant admin.
        boolean allowed = principal.role() == Role.TENANT_ADMIN
                || appt.getCustomerId().equals(principal.userId());
        if (!allowed && principal.role() == Role.PROVIDER) {
            allowed = providers.findByTenantIdAndUserId(tenantId, principal.userId())
                    .map(p -> slots.findByIdAndTenantId(appt.getSlotId(), tenantId)
                            .map(s -> s.getProviderId().equals(p.getId()))
                            .orElse(false))
                    .orElse(false);
        }
        if (!allowed) throw new ApiException.Forbidden("Not your appointment");

        TimeSlot slot = slots.findByIdAndTenantIdForUpdate(appt.getSlotId(), tenantId)
                .orElseThrow(() -> new ApiException.NotFound("Time slot not found"));
        appt.setStatus(AppointmentStatus.CANCELLED);
        slot.setStatus(SlotStatus.AVAILABLE);
        appointments.save(appt);
        slots.save(slot);
        availability.slotChanged(slot, sse);
        return appt;
    }

    @Transactional(readOnly = true)
    public List<Appointment> myBookings(Long tenantId, Long customerId) {
        return appointments.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customerId);
    }

    @Transactional(readOnly = true)
    public List<Appointment> providerDay(Long tenantId, Long providerUserId, LocalDate date) {
        Provider provider = providers.findByTenantIdAndUserId(tenantId, providerUserId)
                .orElseThrow(() -> new ApiException.NotFound("Provider profile not found"));
        LocalDateTime from = date.atStartOfDay();
        return appointments.findByTenantAndProviderAndDay(tenantId, provider.getId(), from, from.plusDays(1));
    }

    @Transactional(readOnly = true)
    public List<Appointment> tenantDay(Long tenantId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        return appointments.findByTenantAndDay(tenantId, from, from.plusDays(1));
    }

    /** Enriches an appointment with slot/customer/service names for dashboards. */
    @Transactional(readOnly = true)
    public AppointmentView view(Long tenantId, Appointment appt) {
        TimeSlot slot = slots.findByIdAndTenantId(appt.getSlotId(), tenantId).orElse(null);
        String customerName = users.findById(appt.getCustomerId()).map(AppUser::getFullName).orElse("?");
        String serviceName = services.findByIdAndTenantId(appt.getServiceId(), tenantId)
                .map(ClinicService::getName).orElse("?");
        Long providerId = slot != null ? slot.getProviderId() : null;
        return new AppointmentView(appt.getId(), appt.getSlotId(), providerId, appt.getCustomerId(),
                customerName, appt.getServiceId(), serviceName,
                slot != null ? slot.getStartAt() : null, slot != null ? slot.getEndAt() : null,
                appt.getStatus().name(), appt.getPaymentRef(), appt.getNotes());
    }

    public record AppointmentView(Long id, Long slotId, Long providerId, Long customerId,
                                  String customerName, Long serviceId, String serviceName,
                                  LocalDateTime startAt, LocalDateTime endAt,
                                  String status, String paymentRef, String notes) {}
}
