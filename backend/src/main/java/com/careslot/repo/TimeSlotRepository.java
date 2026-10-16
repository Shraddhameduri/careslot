package com.careslot.repo;

import com.careslot.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    List<TimeSlot> findByTenantIdAndProviderIdAndStartAtBetweenOrderByStartAt(
            Long tenantId, Long providerId, LocalDateTime from, LocalDateTime to);

    boolean existsByTenantIdAndProviderIdAndStartAt(Long tenantId, Long providerId, LocalDateTime startAt);

    /** Pessimistic lock: serializes concurrent booking attempts on the same slot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TimeSlot s where s.id = :id and s.tenantId = :tenantId")
    Optional<TimeSlot> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    Optional<TimeSlot> findByIdAndTenantId(Long id, Long tenantId);
}
