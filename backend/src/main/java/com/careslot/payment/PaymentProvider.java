package com.careslot.payment;

/**
 * Abstraction over a Stripe-style PaymentIntent flow.
 * Swapping in a real provider means implementing this interface against the
 * Stripe SDK — the booking code never touches provider specifics.
 */
public interface PaymentProvider {

    /** Human-readable provider name, e.g. "mock-stripe". */
    String name();

    IntentResult createIntent(int amountCents, String currency);

    IntentResult confirm(String providerRef, boolean succeed);

    record IntentResult(String providerRef, String clientSecret, String status) {}
}
