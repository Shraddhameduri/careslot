package com.careslot;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auth: signup, login, refresh rotation, reuse detection, logout. */
@Transactional
class AuthFlowTest extends BaseIT {

    @Test
    void signupTenant_createsTenantAndAdmin() throws Exception {
        var res = postJson("/api/v1/auth/signup-tenant",
                Map.of("tenantName", "Bright Smiles", "adminName", "Bo Admin",
                        "adminEmail", "bo@bright.co", "password", "Password123!"), null, null)
                .andExpect(status().isCreated()).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(extract(body, "/slug")).isEqualTo("bright-smiles");
        assertThat(extract(body, "/user/role")).isEqualTo("TENANT_ADMIN");
        assertThat(extract(body, "/accessToken")).isNotBlank();
        assertThat(extract(body, "/refreshToken")).isNotBlank();
    }

    @Test
    void login_wrongPassword_401() throws Exception {
        postJson("/api/v1/auth/login",
                Map.of("email", customerA.user().getEmail(), "password", "WrongPass1!"),
                null, tenantA)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_rotates_andOldTokenRejected() throws Exception {
        String refresh = customerA.tokens().refreshToken();
        var res = postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh), null, tenantA)
                .andExpect(status().isOk()).andReturn();
        String body = res.getResponse().getContentAsString();
        String newRefresh = extract(body, "/refreshToken");
        assertThat(newRefresh).isNotBlank().isNotEqualTo(refresh);

        // Reuse of the old (now revoked) refresh token -> 401 (reuse detection).
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh), null, tenantA)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesRefreshToken() throws Exception {
        String refresh = customerA.tokens().refreshToken();
        mvc.perform(post("/api/v1/auth/logout")
                        .header("Content-Type", "application/json")
                        .content(om.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isNoContent());
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh), null, tenantA)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_duplicateEmailInSameTenant_409() throws Exception {
        String email = "dup" + System.nanoTime() + "@t.co";
        auth.register(tenantA, email, "Dup", "Password123!");
        postJson("/api/v1/auth/register",
                Map.of("email", email, "password", "Password123!", "fullName", "Dup2"),
                null, tenantA)
                .andExpect(status().isConflict());
    }
}
