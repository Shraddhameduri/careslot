package com.careslot.repo;

import com.careslot.domain.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {
    List<WorkingHours> findByTenantIdAndProviderId(Long tenantId, Long providerId);
    Optional<WorkingHours> findByTenantIdAndProviderIdAndDayOfWeek(Long tenantId, Long providerId, int dayOfWeek);
}
