package com.ispf.server.federation;

import tools.jackson.databind.ObjectMapper;
import com.ispf.server.security.acl.VariableAclRequestContext;
import com.ispf.server.tenant.TenantScopeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

/**
 * Replaces an authenticated federation channel principal with its delegated user for one request.
 */
@Component
public class FederationOnBehalfOfFilter extends OncePerRequestFilter {

    private final TenantScopeService tenantScopeService;
    private final ObjectMapper objectMapper;

    public FederationOnBehalfOfFilter(
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        this.tenantScopeService = tenantScopeService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String onBehalfUser = request.getHeader(FederationDelegatedPrincipal.HEADER_ON_BEHALF_USER);
        if (onBehalfUser == null || onBehalfUser.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication channelAuthentication =
                SecurityContextHolder.getContext().getAuthentication();
        Authentication delegatedAuthentication;
        try {
            delegatedAuthentication = FederationDelegatedPrincipal.installFromHttpChannel(
                    channelAuthentication,
                    onBehalfUser,
                    FederationDelegatedPrincipal.parseRolesHeader(
                            request.getHeader(FederationDelegatedPrincipal.HEADER_ON_BEHALF_ROLES)
                    ),
                    request.getHeader(FederationDelegatedPrincipal.HEADER_ON_BEHALF_TENANT),
                    tenantScopeService
            );
        } catch (ResponseStatusException exception) {
            writeError(response, exception);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(delegatedAuthentication);
        try {
            callAsMember(delegatedAuthentication, request, response, filterChain);
        } finally {
            if (channelAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(channelAuthentication);
            }
        }
    }

    private static void callAsMember(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            VariableAclRequestContext.callAsMember(authentication, () -> {
                try {
                    filterChain.doFilter(request, response);
                } catch (ServletException | IOException exception) {
                    throw new FilterChainException(exception);
                }
                return null;
            });
        } catch (FilterChainException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw (ServletException) exception.getCause();
        }
    }

    private void writeError(
            HttpServletResponse response,
            ResponseStatusException exception
    ) throws IOException {
        String reason = exception.getReason() != null
                ? exception.getReason()
                : exception.getStatusCode().toString();
        response.setStatus(exception.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("error", reason));
    }

    private static final class FilterChainException extends RuntimeException {

        private FilterChainException(Exception cause) {
            super(cause);
        }
    }
}
