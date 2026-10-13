package com.ispf.server.application.uipack;

import java.util.Locale;

/**
 * Shared checks for hosted ui-pack ids, Referer paths, and partner trust.
 */
public final class UiPackSupport {

    public static final String APP_HEADER = "X-ISPF-Ui-Pack-App";

    private UiPackSupport() {
    }

    public static boolean isSafeAppId(String appId) {
        if (appId == null || appId.isBlank() || appId.length() > 64 || appId.contains("..")) {
            return false;
        }
        for (int i = 0; i < appId.length(); i++) {
            char c = appId.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    /**
     * App id from a Referer such as {@code https://host/apps/oil-control/}.
     * Returns null when the header is missing or the id is not a safe app id.
     */
    public static String refererAppId(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        int marker = referer.indexOf("/apps/");
        if (marker < 0) {
            return null;
        }
        String rest = referer.substring(marker + "/apps/".length());
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String appId = rest.substring(0, end);
        return isSafeAppId(appId) ? appId : null;
    }

    /**
     * Partner class: {@code partner} or {@code signedPartner} (hyphens and case ignored).
     * {@code firstParty} and {@code devOnly} are not partner.
     */
    public static boolean isPartnerTrust(String trust) {
        if (trust == null || trust.isBlank()) {
            return false;
        }
        String normalized = trust.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return "partner".equals(normalized) || "signedpartner".equals(normalized);
    }
}
