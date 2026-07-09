package com.ispf.server.history;

/**
 * Destination for cold-tier Parquet objects (filesystem or S3-compatible mount).
 */
public interface ColdArchiveSink {

    void put(String objectKey, byte[] content);

    boolean isConfigured();
}
