package com.ispf.server.tenant;

import com.ispf.server.config.TenantIsolationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

/**
 * Validates tenant identifiers against hard-mode schema naming rules — BL-155 stub.
 */
@Component
public class TenantIsolationValidator {

    private static final Pattern PG_SCHEMA_SUFFIX = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private final TenantIsolationProperties properties;

    public TenantIsolationValidator(TenantIsolationProperties properties) {
        this.properties = properties;
    }

    public void validateTenantIdForCreate(String tenantId) {
        if (!properties.isHardMode()) {
            return;
        }
        String schemaName = properties.schemaNameForTenant(tenantId);
        if (schemaName.length() > 63) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hard mode schema name exceeds 63 characters: " + schemaName
            );
        }
        if (!PG_SCHEMA_SUFFIX.matcher(tenantId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hard mode tenantId must match [a-z][a-z0-9_]{0,62} for schema prefix "
                            + properties.getSchemaPrefix()
            );
        }
    }

    public String resolveSchemaName(String tenantId) {
        return properties.schemaNameForTenant(tenantId);
    }
}
