package com.ispf.server.application.data;

import com.ispf.server.spi.WorkflowPlatformSql;
import org.springframework.stereotype.Service;

@Service
public class WorkflowPlatformSqlService implements WorkflowPlatformSql {

    private final ApplicationSchemaSession schemaSession;
    private final PlatformSqlCatalog platformSqlCatalog;

    public WorkflowPlatformSqlService(
            ApplicationSchemaSession schemaSession,
            PlatformSqlCatalog platformSqlCatalog
    ) {
        this.schemaSession = schemaSession;
        this.platformSqlCatalog = platformSqlCatalog;
    }

    @Override
    public String table(String name) {
        return platformSqlCatalog.table(name);
    }

    @Override
    public void runWithPlatformCatalog(Runnable action) {
        schemaSession.runWithPlatformCatalog(action);
    }
}
