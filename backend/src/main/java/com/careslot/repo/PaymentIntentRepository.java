package com.careslot.repo;

import com.careslot.domain.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {
    Optional<PaymentIntent> findByProviderRefAndTenantId(String providerRef, Long tenantId);
}
