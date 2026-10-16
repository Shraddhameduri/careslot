package com.careslot;

import com.careslot.domain.PaymentStatus;
import com.careslot.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mock payment provider: Stripe-style intent flow, no real money movement. */
@Transactional
class PaymentMockTest extends BaseIT {

    @Autowired PaymentService payments;

    @Test
    void createAndConfirmIntent_success() {
        var intent = payments.createIntent(tenantA, 5000, "USD");
        assertThat(intent.getProviderRef()).startsWith("mock_pi_");
        assertThat(intent.getStatus()).isEqualTo(PaymentStatus.REQUIRES_CONFIRM);

        var confirmed = payments.confirmIntent(tenantA, intent.getProviderRef(), true);
        assertThat(confirmed.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void confirmIntent_declinedCard_failed() {
        var intent = payments.createIntent(tenantA, 5000, "USD");
        var failed = payments.confirmIntent(tenantA, intent.getProviderRef(), false);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void providerIsClearlyMock() {
        assertThat(payments.providerName()).isEqualTo("mock-stripe");
    }

    @Test
    void httpEndpoint_labelsMockProvider() throws Exception {
        var res = postJson("/api/v1/payments/mock/intents?amountCents=2500", null,
                customerA.tokens().accessToken(), tenantA)
                .andExpect(status().isCreated()).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(extract(body, "/provider")).isEqualTo("mock-stripe");
        assertThat(extract(body, "/status")).isEqualTo("REQUIRES_CONFIRM");
    }
}
