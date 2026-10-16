package com.careslot.repo;

import com.careslot.domain.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    List<Provider> findByTenantId(Long tenantId);
    List<Provider> findByTenantIdAndActiveTrue(Long tenantId);
    Optional<Provider> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Provider> findByTenantIdAndUserId(Long tenantId, Long userId);
}
