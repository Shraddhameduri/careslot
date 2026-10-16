package com.careslot.payment;

import com.careslot.domain.PaymentIntent;
import com.careslot.domain.PaymentStatus;
import com.careslot.repo.PaymentIntentRepository;
import com.careslot.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists payment intents and delegates the money movement to the provider. */
@Service
public class PaymentService {

    private final PaymentProvider provider;
    private final PaymentIntentRepository intents;

    public PaymentService(PaymentProvider provider, PaymentIntentRepository intents) {
        this.provider = provider;
        this.intents = intents;
    }

    public String providerName() {
        return provider.name();
    }

    @Transactional
    public PaymentIntent createIntent(Long tenantId, int amountCents, String currency) {
        var result = provider.createIntent(amountCents, currency);
        PaymentIntent intent = new PaymentIntent();
        intent.setTenantId(tenantId);
        intent.setProviderRef(result.providerRef());
        intent.setAmountCents(amountCents);
        intent.setCurrency(currency);
        intent.setStatus(PaymentStatus.REQUIRES_CONFIRM);
        return intents.save(intent);
    }

    /** Confirms an intent; {@code succeed=false} simulates a declined card. */
    @Transactional
    public PaymentIntent confirmIntent(Long tenantId, String providerRef, boolean succeed) {
        PaymentIntent intent = intents.findByProviderRefAndTenantId(providerRef, tenantId)
                .orElseThrow(() -> new ApiException.NotFound("Payment intent not found"));
        var result = provider.confirm(providerRef, succeed);
        intent.setStatus(PaymentStatus.valueOf(result.status()));
        return intents.save(intent);
    }
}
