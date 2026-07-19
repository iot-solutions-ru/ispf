package com.ispf.server.function;

import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class FunctionInvokeAccessService {

    private final PrivilegedPlatformFunctionPolicy privilegedPolicy;
    private final ObjectAccessService objectAccessService;
    private final ObjectManager objectManager;

    public FunctionInvokeAccessService(
            PrivilegedPlatformFunctionPolicy privilegedPolicy,
            ObjectAccessService objectAccessService,
            ObjectManager objectManager
    ) {
        this.privilegedPolicy = privilegedPolicy;
        this.objectAccessService = objectAccessService;
        this.objectManager = objectManager;
    }

    /**
     * Enforces object ACL (INVOKE), optional per-function invokeRoles (BL-154),
     * and blocks direct operator invoke of script-only platform functions.
     */
    public void requireDirectInvoke(String objectPath, String functionName, Authentication authentication) {
        List<String> invokeRoles = List.of();
        try {
            PlatformObject node = objectManager.require(objectPath);
            FunctionDescriptor descriptor = node.functions().get(functionName);
            if (descriptor != null) {
                invokeRoles = descriptor.invokeRoles();
            }
        } catch (RuntimeException ignored) {
            // Object missing — let function runtime return its own not-found.
        }
        objectAccessService.requireMemberInvoke(objectPath, "function", functionName, invokeRoles, authentication);
        guardScriptOnlyFunction(objectPath, functionName, authentication);
    }

    /**
     * Called from {@link FunctionService} on every invoke — catches internal callers with a security context.
     */
    public void guardScriptOnlyFunction(String objectPath, String functionName, Authentication authentication) {
        if (!privilegedPolicy.isScriptOnly(objectPath, functionName)) {
            return;
        }
        if (FunctionInvocationScope.isNested() || FunctionInvocationScope.isSystemTrusted()) {
            return;
        }
        Authentication effectiveAuth = authentication != null
                ? authentication
                : SecurityContextHolder.getContext().getAuthentication();
        if (isPrivilegedDirectCaller(effectiveAuth)) {
            return;
        }
        throw scriptOnlyForbidden(objectPath, functionName);
    }

    private boolean isPrivilegedDirectCaller(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return objectAccessService.isConfigurator(authentication);
    }

    private static ResponseStatusException scriptOnlyForbidden(String objectPath, String functionName) {
        return new ResponseStatusException(
                FORBIDDEN,
                "Function " + functionName + " on " + objectPath
                        + " cannot be invoked directly — use a trusted script entry point"
        );
    }
}
