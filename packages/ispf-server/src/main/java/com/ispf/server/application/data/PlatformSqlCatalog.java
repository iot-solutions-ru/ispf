package com.ispf.server.application.data;

import com.ispf.server.relational.RelationalDialect;
import org.springframework.stereotype.Component;

@Component
public class PlatformSqlCatalog {

    private final String schemaPrefix;

    public PlatformSqlCatalog(RelationalDialect relationalDialect) {
        this.schemaPrefix = relationalDialect.platformSchemaPrefix();
    }

    public String table(String name) {
        return schemaPrefix + name;
    }
}
