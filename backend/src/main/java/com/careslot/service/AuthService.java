package com.careslot.service;

import com.careslot.domain.*;
import com.careslot.repo.*;
import com.careslot.security.JwtService;
import com.careslot.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** Signup / login / refresh-token rotation. All user lookups are tenant-scoped. */
@Service
public class AuthService {

    public record TokenPair(String accessToken, String refreshToken) {}
    public record AuthResult(AppUser user, Tenant tenant, TokenPair tokens) {}

    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwt;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();
    private final long refreshDays;

    public AuthService(TenantRepository tenants, AppUserRepository users,
                       RefreshTokenRepository refreshTokens, JwtService jwt,
                       PasswordEncoder passwords,
                       @Value("${app.jwt.refresh-days}") long refreshDays) {
        this.tenants = tenants;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.passwords = passwords;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public AuthResult signupTenant(String tenantName, String adminName, String adminEmail, String password) {
        String slug = slugify(tenantName);
        String base = slug;
        int i = 2;
        while (tenants.existsBySlug(slug)) slug = base + "-" + (i++);
        Tenant tenant = new Tenant();
        tenant.setName(tenantName);
        tenant.setSlug(slug);
        tenants.save(tenant);

        AppUser admin = createUser(tenant.getId(), adminEmail, adminName, password, Role.TENANT_ADMIN);
        return new AuthResult(admin, tenant, issueTokens(admin));
    }

    @Transactional
    public AuthResult register(Long tenantId, String email, String fullName, String password) {
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new ApiException.NotFound("Tenant not found"));
        if (users.existsByTenantIdAndEmail(tenantId, email.toLowerCase(Locale.ROOT))) {
            throw new ApiException.Conflict("Email already registered for this clinic");
        }
        AppUser user = createUser(tenantId, email, fullName, password, Role.CUSTOMER);
        return new AuthResult(user, tenant, issueTokens(user));
    }

    @Transactional
    public AuthResult login(Long tenantId, String email, String password) {
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new ApiException.NotFound("Tenant not found"));
        AppUser user = users.findByTenantIdAndEmail(tenantId, email.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException.Unauthorized("Invalid email or password"));
        if (!passwords.matches(password, user.getPasswordHash())) {
            throw new ApiException.Unauthorized("Invalid email or password");
        }
        return new AuthResult(user, tenant, issueTokens(user));
    }

    /** Rotates the refresh token; re-use of a revoked token revokes the whole family. */
    @Transactional
    public AuthResult refresh(String refreshToken) {
        String hash = sha256(refreshToken);
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException.Unauthorized("Invalid refresh token"));
        if (stored.isRevoked()) {
            // Possible token theft: revoke the entire family.
            refreshTokens.deleteByUserId(stored.getUserId());
            throw new ApiException.Unauthorized("Refresh token revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokens.delete(stored);
            throw new ApiException.Unauthorized("Refresh token expired");
        }
        stored.setRevoked(true);
        refreshTokens.save(stored);
        AppUser user = users.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException.Unauthorized("User no longer exists"));
        Tenant tenant = tenants.findById(user.getTenantId())
                .orElseThrow(() -> new ApiException.Unauthorized("Tenant no longer exists"));
        return new AuthResult(user, tenant, issueTokens(user));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(sha256(refreshToken)).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokens.save(t);
        });
    }

    private AppUser createUser(Long tenantId, String email, String fullName, String password, Role role) {
        AppUser user = new AppUser();
        user.setTenantId(tenantId);
        user.setEmail(email.toLowerCase(Locale.ROOT).trim());
        user.setFullName(fullName.trim());
        user.setPasswordHash(passwords.encode(password));
        user.setRole(role);
        return users.save(user);
    }

    private TokenPair issueTokens(AppUser user) {
        String access = jwt.issueAccessToken(user.getId(), user.getTenantId(), user.getRole());
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        RefreshToken stored = new RefreshToken();
        stored.setUserId(user.getId());
        stored.setTokenHash(sha256(refresh));
        stored.setExpiresAt(LocalDateTime.now().plusDays(refreshDays));
        stored.setRevoked(false);
        refreshTokens.save(stored);
        return new TokenPair(access, refresh);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isEmpty() ? "clinic" : slug;
    }
}
