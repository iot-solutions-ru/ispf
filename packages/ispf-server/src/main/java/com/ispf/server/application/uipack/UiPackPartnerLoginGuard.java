package com.ispf.server.application.uipack;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * ADR-0073: a partner ui-pack must not mint a full console token by calling login
 * from its own page. The check uses {@code Referer}; a pack that omits it is not caught.
 */
@Component
public class UiPackPartnerLoginGuard {

    private static final Logger log = LoggerFactory.getLogger(UiPackPartnerLoginGuard.class);

    private final DropInUiPackLoader uiPackLoader;

    public UiPackPartnerLoginGuard(DropInUiPackLoader uiPackLoader) {
        this.uiPackLoader = uiPackLoader;
    }

    public void rejectIfPartnerPack(HttpServletRequest request) {
        String appId = UiPackSupport.refererAppId(request.getHeader("Referer"));
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
}
