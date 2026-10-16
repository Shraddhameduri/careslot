package com.careslot.repo;

import com.careslot.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    Optional<Appointment> findByIdAndTenantId(Long id, Long tenantId);
    List<Appointment> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(Long tenantId, Long customerId);

    @Query("select a from Appointment a join TimeSlot s on a.slotId = s.id " +
           "where a.tenantId = :tenantId and s.providerId = :providerId " +
           "and s.startAt >= :from and s.startAt < :to order by s.startAt")
    List<Appointment> findByTenantAndProviderAndDay(@Param("tenantId") Long tenantId,
                                                   @Param("providerId") Long providerId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    @Query("select a from Appointment a join TimeSlot s on a.slotId = s.id " +
           "where a.tenantId = :tenantId and s.startAt >= :from and s.startAt < :to order by s.startAt")
    List<Appointment> findByTenantAndDay(@Param("tenantId") Long tenantId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);
}
