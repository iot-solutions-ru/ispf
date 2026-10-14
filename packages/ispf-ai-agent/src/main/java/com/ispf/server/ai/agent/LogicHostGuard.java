package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectTreePort;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Poka-yoke for function hosts (ADR-0051 × ADR-0060). Schema enum is the first gate;
 * this check rejects a missing kind, {@code DEVICE}, and a live {@link ObjectType#DEVICE} path.
 */
final class LogicHostGuard {

    static final List<String> HOST_KINDS = List.of("SINGLETON", "INSTANCE");

    static final String HINT =
            "Set hostKind to SINGLETON (hub, prefer root.platform.singleton-blueprints.*) "
                    + "or INSTANCE (twin). DEVICE is telemetry only (ADR-0060).";

    private LogicHostGuard() {
    }

    static boolean allowedKind(String hostKind) {
        if (hostKind == null || hostKind.isBlank()) {
            return false;
        }
        return HOST_KINDS.contains(hostKind.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * @return error envelope, or null when the host may receive a function
     */
    static Map<String, Object> rejectDeviceHost(String hostKind, String path, ObjectType liveType) {
        if (!allowedKind(hostKind)) {
            String shown = hostKind == null || hostKind.isBlank() ? "(missing)" : hostKind.trim();
            return AgentToolErrors.error(
                    "LOGIC_HOST_DEVICE",
                    "hostKind " + shown + " cannot host solution logic. "
                            + "Declare SINGLETON or INSTANCE; there is no DEVICE default.",
                    path,
                    HINT,
                    AgentToolErrors.DOC_REF_0060
            );
        }
        if (liveType == ObjectType.DEVICE) {
            return AgentToolErrors.error(
                    "LOGIC_HOST_DEVICE",
                    "Path '" + path + "' is ObjectType.DEVICE. Move the function to a SINGLETON hub or INSTANCE twin.",
                    path,
                    HINT,
                    AgentToolErrors.DOC_REF_0060
            );
        }
        return null;
    }

    static ObjectType liveType(ObjectTreePort port, String path) {
        if (port == null || path == null || path.isBlank()) {
            return null;
        }
        try {
            PlatformObject object = port.require(path);
            return object == null ? null : object.type();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
