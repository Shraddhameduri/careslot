package com.careslot.web;

import com.careslot.payment.PaymentService;
import com.careslot.tenant.TenantContext;
import com.careslot.web.dto.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Mock payment endpoints. These exercise the Stripe-style PaymentIntent flow
 * against the MOCK provider only — no real money movement.
 */
@RestController
@RequestMapping("/api/v1/payments/mock")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/intents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public PaymentIntentDto createIntent(@RequestParam int amountCents,
                                         @RequestParam(defaultValue = "USD") String currency) {
        var intent = payments.createIntent(TenantContext.require(), amountCents, currency);
        return new PaymentIntentDto(intent.getProviderRef(), "mock_client_secret",
                intent.getStatus().name(), intent.getAmountCents(), intent.getCurrency(),
                payments.providerName());
    }

    @PostMapping("/intents/{ref}/confirm")
    @PreAuthorize("isAuthenticated()")
    public PaymentIntentDto confirm(@PathVariable("ref") String ref,
                                    @Valid @RequestBody ConfirmPaymentRequest req) {
        var intent = payments.confirmIntent(TenantContext.require(), ref, req.succeed());
        return new PaymentIntentDto(intent.getProviderRef(), null,
                intent.getStatus().name(), intent.getAmountCents(), intent.getCurrency(),
                payments.providerName());
    }
}
