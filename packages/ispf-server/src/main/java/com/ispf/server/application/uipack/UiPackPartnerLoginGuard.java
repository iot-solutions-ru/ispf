package com.ispf.server.application.uipack;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * ADR-0073: a partner ui-pack must not mint a full console token by calling login
 * from its own page. Signals (any one is enough):
 * <ul>
 *   <li>{@code Referer} under {@code /apps/&lt;appId&gt;/}</li>
 *   <li>{@code X-ISPF-Ui-Pack-App} header</li>
 *   <li>{@code ISPF_UI_PACK_CTX} cookie set when serving partner pack HTML</li>
 * </ul>
 */
@Component
public class UiPackPartnerLoginGuard {

    private static final Logger log = LoggerFactory.getLogger(UiPackPartnerLoginGuard.class);

    private final DropInUiPackLoader uiPackLoader;

    public UiPackPartnerLoginGuard(DropInUiPackLoader uiPackLoader) {
        this.uiPackLoader = uiPackLoader;
    }

    public void rejectIfPartnerPack(HttpServletRequest request) {
        String appId = resolveAppId(request);
        if (appId == null) {
            return;
        }
        if (!UiPackSupport.isPartnerTrust(uiPackLoader.uiPackTrust(appId))) {
            return;
        }
        log.warn("Rejected console login from partner ui-pack {}", appId);
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Partner ui-packs cannot mint a console login token from /apps/" + appId
                        + "/. Use a BFF-scoped token (claim uiPackAppId)."
        );
    }

    private static String resolveAppId(HttpServletRequest request) {
        String fromReferer = UiPackSupport.refererAppId(request.getHeader("Referer"));
        if (fromReferer != null) {
            return fromReferer;
        }
        String header = request.getHeader(UiPackSupport.APP_HEADER);
        if (UiPackSupport.isSafeAppId(header)) {
            return header.trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (UiPackSupport.CONTEXT_COOKIE.equals(cookie.getName())
                    && UiPackSupport.isSafeAppId(cookie.getValue())) {
                return cookie.getValue().trim();
            }
        }
        return null;
    }
}
