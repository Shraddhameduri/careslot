package com.careslot;

import com.careslot.domain.*;
import com.careslot.repo.*;
import com.careslot.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class BaseIT {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;
    @Autowired protected AuthService auth;
    @Autowired protected TenantRepository tenants;
    @Autowired protected AppUserRepository users;
    @Autowired protected ClinicServiceRepository services;
    @Autowired protected ProviderRepository providers;
    @Autowired protected TimeSlotRepository slots;
    @Autowired protected AppointmentRepository appointments;

    protected AuthService.AuthResult adminA;
    protected AuthService.AuthResult customerA;
    protected long tenantA;

    @BeforeEach
    void baseSetUp() {
        adminA = auth.signupTenant("Test Clinic " + System.nanoTime(), "Admin", "admin" + System.nanoTime() + "@t.co", "Password123!");
        tenantA = adminA.tenant().getId();
        customerA = auth.register(tenantA, "cust" + System.nanoTime() + "@t.co", "Cust", "Password123!");
    }

    protected ResultActions postJson(String url, Object body, String accessToken, Long tenantId) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON);
        if (body != null) req.content(om.writeValueAsString(body));
        if (accessToken != null) req.header("Authorization", "Bearer " + accessToken);
        if (tenantId != null) req.header("X-Tenant-Id", tenantId);
        return mvc.perform(req);
    }

    protected String extract(String json, String path) throws Exception {
        return om.readTree(json).at(path).asText();
    }

    protected Long extractLong(String json, String path) throws Exception {
        return om.readTree(json).at(path).asLong();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> loginAs(Long tenantId, String email, String password) throws Exception {
        var res = postJson("/api/v1/auth/login", Map.of("email", email, "password", password), null, tenantId)
                .andExpect(status().isOk()).andReturn();
        return om.readValue(res.getResponse().getContentAsString(), Map.class);
    }
}
