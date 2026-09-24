package com.ispf.server.spi;

/** Platform-catalog table name and a callback that runs inside that catalog. */
public interface WorkflowPlatformSql {

    String table(String name);

    void runWithPlatformCatalog(Runnable action);
}
