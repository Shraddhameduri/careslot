package com.careslot.tenant;

/** Holds the current request's tenant id; set by TenantContextFilter, cleared after. */
public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long tenantId) { CURRENT.set(tenantId); }

    public static Long get() { return CURRENT.get(); }

    /** Returns the tenant id or throws 400 if the request carries no tenant. */
    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new com.careslot.web.ApiException.BadRequest(
                    "Tenant is required: send X-Tenant-Id header or ?tenant=<slug>");
        }
        return id;
    }

    public static void clear() { CURRENT.remove(); }
}
