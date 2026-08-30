package com.ispf.server.security.acl;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Selects whether variable member ACLs are enforced for the current execution.
 * <p>
 * {@code SYSTEM} is the default for schedulers, materializers, and engine background work.
 * {@code MEMBER} is for interactive HTTP, agent, and tunnel calls made on behalf of a user.
 */
public final class VariableAclRequestContext {

    private static final ThreadLocal<EnforcementMode> ENFORCEMENT_MODE = new ThreadLocal<>();
    private static final ThreadLocal<Authentication> MEMBER_AUTHENTICATION = new ThreadLocal<>();

    private VariableAclRequestContext() {
    }

    public static <T> T callAsMember(Supplier<T> action) {
        Authentication authentication = MEMBER_AUTHENTICATION.get();
        if (authentication == null) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
        return callAsMember(authentication, action);
    }

    public static <T> T callAsMember(Authentication authentication, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        EnforcementMode previousMode = ENFORCEMENT_MODE.get();
        Authentication previousAuthentication = MEMBER_AUTHENTICATION.get();
        ENFORCEMENT_MODE.set(EnforcementMode.MEMBER);
        if (authentication == null) {
            MEMBER_AUTHENTICATION.remove();
        } else {
            MEMBER_AUTHENTICATION.set(authentication);
        }
        try {
            return action.get();
        } finally {
            restore(ENFORCEMENT_MODE, previousMode);
            restore(MEMBER_AUTHENTICATION, previousAuthentication);
        }
    }

    public static void runAsMember(Runnable action) {
        Objects.requireNonNull(action, "action");
        callAsMember(() -> {
            action.run();
            return null;
        });
    }

    public static boolean isMemberEnforced() {
        return currentMode() == EnforcementMode.MEMBER;
    }

    public static Authentication requireAuthentication() {
        Authentication authentication = MEMBER_AUTHENTICATION.get();
        if (authentication == null) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Authentication required for variable ACL"
            );
        }
        return authentication;
    }

    private static EnforcementMode currentMode() {
        EnforcementMode mode = ENFORCEMENT_MODE.get();
        return mode != null ? mode : EnforcementMode.SYSTEM;
    }

    private static <T> void restore(ThreadLocal<T> context, T previous) {
        if (previous == null) {
            context.remove();
        } else {
            context.set(previous);
        }
    }

    private enum EnforcementMode {
        SYSTEM,
        MEMBER
    }
}
