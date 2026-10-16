package com.careslot.payment;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MOCK implementation of a Stripe-style PaymentIntent flow, for local
 * development, offline demos, and tests. <b>This is NOT a real Stripe
 * integration</b> — no network calls are made and no money moves.
 * Replace with a Stripe SDK-backed implementation for production.
 */
@Component
public class MockStripePaymentProvider implements PaymentProvider {

    private final ConcurrentHashMap<String, String> states = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "mock-stripe";
    }

    @Override
    public IntentResult createIntent(int amountCents, String currency) {
        String ref = "mock_pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String secret = "mock_secret_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        states.put(ref, "REQUIRES_CONFIRM");
        return new IntentResult(ref, secret, "REQUIRES_CONFIRM");
    }

    @Override
    public IntentResult confirm(String providerRef, boolean succeed) {
        if (!states.containsKey(providerRef)) {
            throw new IllegalArgumentException("Unknown payment intent: " + providerRef);
        }
        String status = succeed ? "CONFIRMED" : "FAILED";
        states.put(providerRef, status);
        return new IntentResult(providerRef, null, status);
    }
}
