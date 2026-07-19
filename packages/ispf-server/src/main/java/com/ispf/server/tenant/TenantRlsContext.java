package com.ispf.server.tenant;

/**
 * Per-request PostgreSQL RLS session context (BL-155).
 * Applied to pooled connections via {@link TenantRlsSession} on checkout.
 */
public final class TenantRlsContext {

    public record State(boolean bypass, String tenantId) {
        public static State allowAll() {
            return new State(true, "");
        }

        public static State forTenant(String tenantId) {
            return new State(false, tenantId == null ? "" : tenantId);
        }
    }

    private static final ThreadLocal<State> HOLDER = new ThreadLocal<>();

    private TenantRlsContext() {
    }

    public static void set(State state) {
        if (state == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(state);
        }
    }

    public static void setBypass() {
        set(State.allowAll());
    }

    public static void setTenant(String tenantId) {
        set(State.forTenant(tenantId));
    }

    /**
     * Current state, or {@code null} when unset (Flyway / bootstrap → policy default-allow).
     */
    public static State get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
