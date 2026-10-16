package com.careslot.tenant;

import com.careslot.repo.TenantRepository;
import com.careslot.security.TenantPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves the request's tenant AFTER the JWT filter:
 * authenticated requests use the tenant claim; public requests use the
 * X-Tenant-Id header or the ?tenant=&lt;slug&gt; query parameter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantRepository tenants;

    public TenantContextFilter(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof TenantPrincipal p) {
                TenantContext.set(p.tenantId());
            } else {
                String header = request.getHeader("X-Tenant-Id");
                if (header != null && !header.isBlank()) {
                    TenantContext.set(Long.valueOf(header.trim()));
                } else {
                    String slug = request.getParameter("tenant");
                    if (slug != null && !slug.isBlank()) {
                        tenants.findBySlug(slug.trim()).ifPresent(t -> TenantContext.set(t.getId()));
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
