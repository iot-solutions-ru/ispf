package com.ispf.server.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.ispf.server.config.CassandraStoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around the DataStax Java driver (Cassandra and Scylla compatible).
 */
public class CassandraClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CassandraClient.class);

    private final CqlSession session;
    private final String keyspace;

    public CassandraClient(CassandraStoreProperties settings) {
        CassandraSchemaBootstrap.ensureKeyspaceExists(settings);
        this.keyspace = CassandraSchemaBootstrap.requireValidKeyspace(settings.getKeyspace());
        this.session = CassandraSchemaBootstrap.openSession(settings, keyspace);
        log.info(
                "Cassandra session opened (keyspace={}, datacenter={}, contacts={})",
                keyspace,
                settings.getLocalDatacenter(),
                settings.getContactPoints()
        );
    }

    public void execute(String cql) {
        session.execute(SimpleStatement.newInstance(cql).setTimeout(Duration.ofSeconds(30)));
    }

    public void execute(Statement<?> statement) {
        session.execute(statement.setTimeout(Duration.ofSeconds(30)));
    }

    /** Fire-and-forget CQL (errors logged); use for non-critical hot-path side effects such as counter bumps. */
    public void executeAsync(Statement<?> statement) {
        session.executeAsync(statement.setTimeout(Duration.ofSeconds(30)))
                .whenComplete((resultSet, error) -> {
                    if (error != null) {
                        log.warn("Async CQL failed: {}", error.getMessage());
                    }
                });
    }

    public void executeBatch(List<BoundStatement> statements) {
        if (statements.isEmpty()) {
            return;
        }
        execute(toBatchStatement(statements));
    }

    private CompletableFuture<Void> executeBatchAsync(List<BoundStatement> statements) {
        if (statements.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Statement<?> statement = toBatchStatement(statements);
        return session.executeAsync(statement)
                .toCompletableFuture()
                .thenApply(resultSet -> null);
    }

    private Statement<?> toBatchStatement(List<BoundStatement> statements) {
        if (statements.size() == 1) {
            return statements.getFirst().setTimeout(Duration.ofSeconds(30));
        }
        return BatchStatement.builder(BatchType.UNLOGGED)
                .addStatements(statements.toArray(BoundStatement[]::new))
                .build()
                .setTimeout(Duration.ofSeconds(30));
    }

    /**
     * Executes same-partition batches concurrently via the driver's async API.
     * Each inner list must target a single CQL partition (never mix partitions in one batch).
     */
    public void executePartitionBatches(List<List<BoundStatement>> partitionBatches, int maxParallel) {
        if (partitionBatches.isEmpty()) {
            return;
        }
        if (partitionBatches.size() == 1) {
            executeBatch(partitionBatches.getFirst());
            return;
        }
        int parallelism = Math.max(1, Math.min(maxParallel, partitionBatches.size()));
        List<CompletableFuture<Void>> futures = new ArrayList<>(partitionBatches.size());
        for (List<BoundStatement> batch : partitionBatches) {
            futures.add(executeBatchAsync(batch));
            if (futures.size() >= parallelism) {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                futures.clear();
            }
        }
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
    }

    public PreparedStatement prepare(String cql) {
        return session.prepare(cql);
    }

    public ResultSet query(String cql, Object... values) {
        SimpleStatement statement = SimpleStatement.newInstance(cql, values)
                .setTimeout(Duration.ofSeconds(30));
        return session.execute(statement);
    }

    public List<Row> queryRows(Statement<?> statement) {
        return session.execute(statement.setTimeout(Duration.ofSeconds(30))).all();
    }

    public List<Row> queryRows(String cql, Object... values) {
        return query(cql, values).all();
    }

    public String keyspace() {
        return keyspace;
    }

    public String qualifiedTable(String table) {
        return keyspace + "." + table;
    }

    @Override
    public void close() {
        session.close();
    }
}
