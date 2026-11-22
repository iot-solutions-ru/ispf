package com.ispf.server.relational;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Duration;

/**
 * Shared helpers for ADR-0079 MySQL Testcontainers gates.
 * <p>The JDBC client is LGPL MariaDB Connector/J against a MySQL 8.4 server; GPL
 * {@code mysql-connector-j} stays off the classpath. Connector/J 3.x accepts {@code jdbc:mysql:}
 * only with {@code permitMysqlScheme}. Testcontainers probes readiness through
 * {@link MySQLContainer#getDriverClassName()}, so the container must name the MariaDB driver.
 * <p>Server flags are the lab cell: READ-COMMITTED (PostgreSQL default isolation) and
 * utf8mb4 / {@code utf8mb4_0900_bin} (case-sensitive object paths, like the MSSQL CS collation).
 */
final class MysqlTestSupport {

    static final String IMAGE = "mysql:8.4";
    static final String DRIVER_CLASS = "org.mariadb.jdbc.Driver";
    static final String ISPF_DB = "ispf";

    private MysqlTestSupport() {
    }

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            return false;
        }
    }

    @SuppressWarnings("resource")
    static MySQLContainer newContainer() {
        return new MySQLContainer(IMAGE) {
            @Override
            public String getDriverClassName() {
                return DRIVER_CLASS;
            }
        }
                .withDatabaseName(ISPF_DB)
                .withUrlParam("permitMysqlScheme", "true")
                .withCommand(
                        "--transaction-isolation=READ-COMMITTED",
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_0900_bin"
                )
                .withStartupTimeout(Duration.ofMinutes(5));
    }
}
