package com.ispf.server.federation;

public enum CatalogSyncConflictType {
    /** Local native object occupies the catalog mirror path. */
    LOCAL_NATIVE,
    /** Existing proxy points to a different peer or remote path. */
    PROXY_MISMATCH
}
