package com.careslot.repo;

import com.careslot.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
