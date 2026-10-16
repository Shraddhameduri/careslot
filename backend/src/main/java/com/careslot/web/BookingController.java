package com.careslot.web;

import com.careslot.security.TenantPrincipal;
import com.careslot.service.BookingService;
import com.careslot.service.BookingService.AppointmentView;
import com.careslot.web.dto.Dtos.BookRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public AppointmentView book(@Valid @RequestBody BookRequest req,
                                @AuthenticationPrincipal TenantPrincipal principal) {
        var appt = bookings.book(principal.tenantId(), principal.userId(),
                req.slotId(), req.serviceId(), req.notes());
        return bookings.view(principal.tenantId(), appt);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<AppointmentView> mine(@AuthenticationPrincipal TenantPrincipal principal) {
        return bookings.myBookings(principal.tenantId(), principal.userId()).stream()
                .map(a -> bookings.view(principal.tenantId(), a))
                .toList();
    }

    @PostMapping("/{id}/cancel")
    public AppointmentView cancel(@PathVariable Long id,
                                  @AuthenticationPrincipal TenantPrincipal principal) {
        var appt = bookings.cancel(principal.tenantId(), principal, id);
        return bookings.view(principal.tenantId(), appt);
    }
}
