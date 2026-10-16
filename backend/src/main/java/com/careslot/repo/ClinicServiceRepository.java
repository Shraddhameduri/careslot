package com.careslot.repo;

import com.careslot.domain.ClinicService;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ClinicServiceRepository extends JpaRepository<ClinicService, Long> {
    List<ClinicService> findByTenantIdAndActiveTrue(Long tenantId);
    List<ClinicService> findByTenantId(Long tenantId);
    Optional<ClinicService> findByIdAndTenantId(Long id, Long tenantId);
}
