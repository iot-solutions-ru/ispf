package com.ispf.server.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.ispf.server.config.CassandraStoreProperties;
import com.ispf.server.storage.StorageStartupRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Ensures Cassandra/Scylla keyspace exists before opening a keyspace-bound session.
 * Table DDL remains in the respective store services.
 */
public final class CassandraSchemaBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CassandraSchemaBootstrap.class);

    private CassandraSchemaBootstrap() {
    }

    public static void ensureKeyspaceExists(CassandraStoreProperties settings) {
        String keyspace = requireValidKeyspace(settings.getKeyspace());
        StorageStartupRetry.run("Cassandra keyspace " + keyspace, () -> {
            try (CqlSession bootstrap = openSession(settings, null)) {
                bootstrap.execute(
                        SimpleStatement.newInstance(
                                """
                                        CREATE KEYSPACE IF NOT EXISTS %s
                                        WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
                                        """
                                        .formatted(keyspace)
                        ).setTimeout(Duration.ofSeconds(30))
                );
                log.info("Cassandra keyspace ready: {}", keyspace);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to create Cassandra keyspace: " + keyspace, ex);
            }
        });
    }

    static CqlSession openSession(CassandraStoreProperties settings, String keyspace) {
        CqlSessionBuilder builder = CqlSession.builder()
                .withLocalDatacenter(settings.getLocalDatacenter());
        if (keyspace != null && !keyspace.isBlank()) {
            builder.withKeyspace(keyspace);
        }
        for (InetSocketAddress contactPoint : parseContactPoints(settings)) {
            builder.addContactPoint(contactPoint);
        }
        if (settings.getUsername() != null && !settings.getUsername().isBlank()) {
            builder.withAuthCredentials(
                    settings.getUsername(),
                    settings.getPassword() != null ? settings.getPassword() : ""
            );
        }
        return builder.build();
    }

    static String requireValidKeyspace(String keyspace) {
        if (keyspace == null || !keyspace.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid Cassandra keyspace name: " + keyspace);
        }
        return keyspace;
    }

    private static List<InetSocketAddress> parseContactPoints(CassandraStoreProperties settings) {
        List<InetSocketAddress> points = new ArrayList<>();
        for (String raw : settings.getContactPoints().split(",")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                points.add(new InetSocketAddress(trimmed, settings.getPort()));
            }
        }
        if (points.isEmpty()) {
            points.add(new InetSocketAddress("127.0.0.1", settings.getPort()));
        }
        return points;
    }
}
