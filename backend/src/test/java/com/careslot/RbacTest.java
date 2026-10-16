package com.careslot;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RBAC: admin-only endpoints reject customers/providers; anonymous gets 401. */
@Transactional
class RbacTest extends BaseIT {

    @Test
    void customer_cannotAccessAdminEndpoints_403() throws Exception {
        mvc.perform(get("/api/v1/admin/services")
                        .header("Authorization", "Bearer " + customerA.tokens().accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_cannotAccessAdminEndpoints_401() throws Exception {
        mvc.perform(get("/api/v1/admin/services")
                        .header("X-Tenant-Id", tenantA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_canAccessAdminEndpoints_200() throws Exception {
        mvc.perform(get("/api/v1/admin/services")
                        .header("Authorization", "Bearer " + adminA.tokens().accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void customer_cannotUseProviderEndpoints_403() throws Exception {
        mvc.perform(get("/api/v1/provider/schedule")
                        .header("Authorization", "Bearer " + customerA.tokens().accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_cannotBook_401() throws Exception {
        postJson("/api/v1/bookings", java.util.Map.of("slotId", 1, "serviceId", 1), null, tenantA)
                .andExpect(status().isUnauthorized());
    }
}
