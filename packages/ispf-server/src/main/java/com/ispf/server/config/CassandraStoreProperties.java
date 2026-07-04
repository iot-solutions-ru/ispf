package com.ispf.server.config;

/**
 * Connection settings for Cassandra or Scylla (CQL-compatible) time-series backends.
 */
public class CassandraStoreProperties {

    /** Comma-separated contact points, e.g. {@code 127.0.0.1,10.0.0.2}. */
    private String contactPoints = "127.0.0.1";
    private int port = 9042;
    private String localDatacenter = "datacenter1";
    private String keyspace = "ispf";
    private String table = "";
    private String username = "";
    private String password = "";
    /** Max CQL statements per UNLOGGED batch within one partition. */
    private int maxStatementsPerPartitionBatch = 200;
    /** Max concurrent same-partition batches (async driver requests). */
    private int maxParallelPartitionBatches = 8;

    public String getContactPoints() {
        return contactPoints;
    }

    public void setContactPoints(String contactPoints) {
        this.contactPoints = contactPoints;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getLocalDatacenter() {
        return localDatacenter;
    }

    public void setLocalDatacenter(String localDatacenter) {
        this.localDatacenter = localDatacenter;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public void setKeyspace(String keyspace) {
        this.keyspace = keyspace;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String resolveTable(String defaultTable) {
        return table != null && !table.isBlank() ? table : defaultTable;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String connectionKey() {
        return contactPoints + ":" + port + "/" + keyspace + "/" + localDatacenter;
    }

    public int getMaxStatementsPerPartitionBatch() {
        return maxStatementsPerPartitionBatch;
    }

    public void setMaxStatementsPerPartitionBatch(int maxStatementsPerPartitionBatch) {
        this.maxStatementsPerPartitionBatch = Math.max(1, maxStatementsPerPartitionBatch);
    }

    public int getMaxParallelPartitionBatches() {
        return maxParallelPartitionBatches;
    }

    public void setMaxParallelPartitionBatches(int maxParallelPartitionBatches) {
        this.maxParallelPartitionBatches = Math.max(1, maxParallelPartitionBatches);
    }
}
