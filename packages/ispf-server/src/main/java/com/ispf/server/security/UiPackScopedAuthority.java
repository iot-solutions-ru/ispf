package com.ispf.server.security;

/**
 * ADR-0073: authentication with this authority is pack-scoped — BFF/read for one appId only;
 * admin mutate APIs must reject it.
 */
public final class UiPackScopedAuthority {

    public static final String CLAIM = "uiPackAppId";
    public static final String ROLE = "ROLE_UI_PACK";

    private UiPackScopedAuthority() {
    }
}
