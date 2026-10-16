package com.careslot;

import com.careslot.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tenant isolation: data created in tenant A is invisible/untouchable from tenant B. */
@Transactional
class TenantIsolationTest extends BaseIT {

    @Test
    void serviceCreatedInTenantA_invisibleInTenantB() throws Exception {
        // Admin A creates a service.
        var created = postJson("/api/v1/admin/services",
                Map.of("name", "Secret Service A", "durationMinutes", 30, "priceCents", 1000),
                adminA.tokens().accessToken(), tenantA)
                .andExpect(status().isCreated()).andReturn();
        Long serviceId = extractLong(created.getResponse().getContentAsString(), "/id");

        // Tenant B: separate signup.
        AuthService.AuthResult adminB = auth.signupTenant("Other Clinic " + System.nanoTime(),
                "Admin B", "adminb" + System.nanoTime() + "@t.co", "Password123!");

        // B's service list must not contain A's service.
        var list = mvc.perform(get("/api/v1/admin/services")
                        .header("Authorization", "Bearer " + adminB.tokens().accessToken()))
                .andExpect(status().isOk()).andReturn();
        assertThat(list.getResponse().getContentAsString()).doesNotContain("Secret Service A");

        // B cannot update or delete A's service (scoped lookup -> 404).
        mvc.perform(put("/api/v1/admin/services/" + serviceId)
                        .header("Authorization", "Bearer " + adminB.tokens().accessToken())
                        .header("Content-Type", "application/json")
                        .content(om.writeValueAsString(
                                Map.of("name", "Hijacked", "durationMinutes", 30, "priceCents", 1))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/admin/services/" + serviceId)
                        .header("Authorization", "Bearer " + adminB.tokens().accessToken()))
                .andExpect(status().isNotFound());

        // Public availability of B with A's provider id yields nothing cross-tenant.
        assertThat(services.findByIdAndTenantId(serviceId, adminB.tenant().getId())).isEmpty();
    }

    @Test
    void loginScopedPerTenant_sameEmailDifferentTenants() throws Exception {
        String email = "shared" + System.nanoTime() + "@t.co";
        auth.register(tenantA, email, "Shared A", "Password123!");
        AuthService.AuthResult adminB = auth.signupTenant("Iso Clinic " + System.nanoTime(),
                "Admin", "iso" + System.nanoTime() + "@t.co", "Password123!");
        auth.register(adminB.tenant().getId(), email, "Shared B", "Password123!");

        // Same email logs into the correct tenant based on X-Tenant-Id.
        var a = loginAs(tenantA, email, "Password123!");
        var b = loginAs(adminB.tenant().getId(), email, "Password123!");
        assertThat(((Map<?, ?>) a.get("user")).get("tenantId").toString())
                .isNotEqualTo(((Map<?, ?>) b.get("user")).get("tenantId").toString());
    }
}
