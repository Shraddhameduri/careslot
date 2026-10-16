package com.careslot.web;

import com.careslot.domain.AppUser;
import com.careslot.domain.Tenant;
import com.careslot.security.RateLimiter;
import com.careslot.service.AuthService;
import com.careslot.tenant.TenantContext;
import com.careslot.web.dto.Dtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final RateLimiter rateLimiter;
    private final int loginCapacity;
    private final int loginRefill;

    public AuthController(AuthService auth, RateLimiter rateLimiter,
                          @Value("${app.rate-limit.login.capacity}") int loginCapacity,
                          @Value("${app.rate-limit.login.refill-per-minute}") int loginRefill) {
        this.auth = auth;
        this.rateLimiter = rateLimiter;
        this.loginCapacity = loginCapacity;
        this.loginRefill = loginRefill;
    }

    private void checkRate(HttpServletRequest req, String endpoint) {
        String key = endpoint + ":" + req.getRemoteAddr();
        if (!rateLimiter.tryConsume(key, loginCapacity, loginRefill)) {
            throw new ApiException.RateLimited("Too many attempts, please try again later");
        }
    }

    private static AuthResponse toResponse(AuthService.AuthResult r) {
        AppUser u = r.user();
        Tenant t = r.tenant();
        return new AuthResponse(
                new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(), u.getTenantId()),
                t.getId(), t.getSlug(),
                r.tokens().accessToken(), r.tokens().refreshToken());
    }

    @PostMapping("/signup-tenant")
    public ResponseEntity<AuthResponse> signupTenant(@Valid @RequestBody TenantSignupRequest req,
                                                     HttpServletRequest http) {
        checkRate(http, "signup");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(auth.signupTenant(req.tenantName(), req.adminName(),
                        req.adminEmail(), req.password())));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
                                                 HttpServletRequest http) {
        checkRate(http, "register");
        Long tenantId = TenantContext.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(auth.register(tenantId, req.email(), req.fullName(), req.password())));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        checkRate(http, "login");
        Long tenantId = TenantContext.require();
        return toResponse(auth.login(tenantId, req.email(), req.password()));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        checkRate(http, "refresh");
        return toResponse(auth.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
