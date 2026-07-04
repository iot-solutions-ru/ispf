package com.ispf.server.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.config.CassandraStoreProperties;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.config.IspfRedisProperties;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.event.EventHistoryRecordCounter;
import com.ispf.server.event.EventJournalStore;
import com.ispf.server.persistence.VariableSampleRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformStorageHealthService {

    private final DataSource dataSource;
    private final EventJournalProperties eventJournalProperties;
    private final VariableHistoryProperties variableHistoryProperties;
    private final EventJournalStore eventJournalStore;
    private final EventHistoryRecordCounter eventHistoryRecordCounter;
    private final VariableSampleRepository variableSampleRepository;
    private final IspfRedisProperties redisProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public PlatformStorageHealthService(
            DataSource dataSource,
            EventJournalProperties eventJournalProperties,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalStore eventJournalStore,
            EventHistoryRecordCounter eventHistoryRecordCounter,
            VariableSampleRepository variableSampleRepository,
            IspfRedisProperties redisProperties,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.dataSource = dataSource;
        this.eventJournalProperties = eventJournalProperties;
        this.variableHistoryProperties = variableHistoryProperties;
        this.eventJournalStore = eventJournalStore;
        this.eventHistoryRecordCounter = eventHistoryRecordCounter;
        this.variableSampleRepository = variableSampleRepository;
        this.redisProperties = redisProperties;
        this.redisTemplate = redisTemplate;
    }

    public StorageHealth snapshot() {
        List<StorageBackendInfo> backends = new ArrayList<>();
        backends.add(relationalBackend());
        backends.add(eventJournalBackend());
        backends.add(variableHistoryBackend());
        backends.add(redisBackend());
        return new StorageHealth(Instant.now().toString(), backends);
    }

    private StorageBackendInfo relationalBackend() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean connected = false;
        String connectionError = null;
        String endpoint = null;
        String engine = "postgresql";

        if (dataSource instanceof HikariDataSource hikari) {
            endpoint = sanitizeJdbcUrl(hikari.getJdbcUrl());
            try (Connection connection = dataSource.getConnection()) {
                connected = connection.isValid(2);
                engine = connection.getMetaData().getDatabaseProductName();
            } catch (Exception ex) {
                connectionError = ex.getMessage();
            }
            HikariDataSource pool = hikari;
            if (pool.getHikariPoolMXBean() != null) {
                details.put("poolName", pool.getPoolName());
                details.put("activeConnections", pool.getHikariPoolMXBean().getActiveConnections());
                details.put("totalConnections", pool.getHikariPoolMXBean().getTotalConnections());
                details.put("maxPoolSize", pool.getMaximumPoolSize());
            }
        } else {
            try (Connection connection = dataSource.getConnection()) {
                connected = connection.isValid(2);
                endpoint = sanitizeJdbcUrl(connection.getMetaData().getURL());
                engine = connection.getMetaData().getDatabaseProductName();
            } catch (Exception ex) {
                connectionError = ex.getMessage();
            }
            details.put("poolAvailable", false);
        }

        return new StorageBackendInfo(
                "relational",
                "relational",
                "jdbc",
                engine,
                endpoint,
                connected,
                connectionError,
                null,
                null,
                details
        );
    }

    private StorageBackendInfo eventJournalBackend() {
        String store = eventJournalProperties.getStore();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("asyncEnabled", eventJournalProperties.isAsyncEnabled());
        details.put("retentionDays", eventJournalProperties.getRetentionDays());

        long recordCount = eventHistoryRecordCounter.isInitialized()
                ? eventHistoryRecordCounter.totalRecords()
                : eventJournalStore.countTotal();

        if (eventJournalProperties.isClickHouseStore()) {
            EventJournalProperties.ClickHouse config = eventJournalProperties.getClickhouse();
            String endpoint = config.getUrl() + " / " + config.getDatabase() + "." + config.getTable();
            details.put("database", config.getDatabase());
            details.put("table", config.getTable());
            ProbeResult probe = probeClickHouse(config.getUrl(), config.getDatabase(), config.getUsername(), config.getPassword());
            if (probe.connected()) {
                Long externalCount = clickHouseCount(
                        config.getUrl(),
                        config.getDatabase(),
                        config.getTable(),
                        config.getUsername(),
                        config.getPassword()
                );
                if (externalCount != null) {
                    recordCount = externalCount;
                }
            }
            return new StorageBackendInfo(
                    "eventJournal",
                    "eventJournal",
                    store,
                    "clickhouse",
                    endpoint,
                    probe.connected(),
                    probe.error(),
                    recordCount,
                    eventJournalProperties.getRetentionDays(),
                    details
            );
        }

        if (eventJournalProperties.isCassandraStore()) {
            CassandraStoreProperties config = eventJournalProperties.getCassandra();
            String table = config.resolveTable("event_history");
            String endpoint = cassandraEndpoint(config);
            details.put("keyspace", config.getKeyspace());
            details.put("table", table);
            details.put("localDatacenter", config.getLocalDatacenter());
            ProbeResult probe = probeCassandra(config);
            return new StorageBackendInfo(
                    "eventJournal",
                    "eventJournal",
                    store,
                    "cassandra",
                    endpoint,
                    probe.connected(),
                    probe.error(),
                    recordCount,
                    eventJournalProperties.getRetentionDays(),
                    details
            );
        }

        details.put("table", "event_history");
        details.put("timescaleEligible", true);
        ProbeResult jdbcProbe = probeJdbc(dataSource);
        return new StorageBackendInfo(
                "eventJournal",
                "eventJournal",
                store,
                "postgresql",
                jdbcEndpoint(dataSource),
                jdbcProbe.connected(),
                jdbcProbe.error() != null ? jdbcProbe.error() : (jdbcProbe.connected() ? null : "JDBC connection check failed"),
                recordCount,
                eventJournalProperties.getRetentionDays(),
                details
        );
    }

    private StorageBackendInfo variableHistoryBackend() {
        String store = variableHistoryProperties.getStore();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("enabled", variableHistoryProperties.isEnabled());
        details.put("asyncEnabled", variableHistoryProperties.isAsyncEnabled());
        details.put("retentionDays", variableHistoryProperties.getRetentionDays());
        details.put("minIntervalMs", variableHistoryProperties.getMinIntervalMs());

        long recordCount = variableHistoryProperties.isExternalTimeSeriesStore()
                ? 0L
                : variableSampleRepository.count();
        Long displayedCount = variableHistoryProperties.isExternalTimeSeriesStore() ? null : recordCount;

        if (variableHistoryProperties.isClickHouseStore()) {
            VariableHistoryProperties.ClickHouse config = variableHistoryProperties.getClickhouse();
            String endpoint = config.getUrl() + " / " + config.getDatabase() + "." + config.getTable();
            details.put("database", config.getDatabase());
            details.put("table", config.getTable());
            ProbeResult probe = probeClickHouse(config.getUrl(), config.getDatabase(), config.getUsername(), config.getPassword());
            if (probe.connected()) {
                Long externalCount = clickHouseCount(
                        config.getUrl(),
                        config.getDatabase(),
                        config.getTable(),
                        config.getUsername(),
                        config.getPassword()
                );
                if (externalCount != null) {
                    displayedCount = externalCount;
                }
            }
            return new StorageBackendInfo(
                    "variableHistory",
                    "variableHistory",
                    store,
                    "clickhouse",
                    endpoint,
                    probe.connected(),
                    probe.error(),
                    displayedCount,
                    variableHistoryProperties.getRetentionDays(),
                    details
            );
        }

        if (variableHistoryProperties.isCassandraStore()) {
            CassandraStoreProperties config = variableHistoryProperties.getCassandra();
            String table = config.resolveTable("variable_samples");
            String endpoint = cassandraEndpoint(config);
            details.put("keyspace", config.getKeyspace());
            details.put("table", table);
            details.put("localDatacenter", config.getLocalDatacenter());
            ProbeResult probe = probeCassandra(config);
            return new StorageBackendInfo(
                    "variableHistory",
                    "variableHistory",
                    store,
                    "cassandra",
                    endpoint,
                    probe.connected(),
                    probe.error(),
                    null,
                    variableHistoryProperties.getRetentionDays(),
                    details
            );
        }

        details.put("table", "variable_samples");
        details.put("timescaleEligible", !"jpa".equalsIgnoreCase(store));
        ProbeResult jdbcProbe = probeJdbc(dataSource);
        return new StorageBackendInfo(
                "variableHistory",
                "variableHistory",
                store,
                "postgresql",
                jdbcEndpoint(dataSource),
                jdbcProbe.connected(),
                jdbcProbe.error() != null ? jdbcProbe.error() : (jdbcProbe.connected() ? null : "JDBC connection check failed"),
                displayedCount,
                variableHistoryProperties.getRetentionDays(),
                details
        );
    }

    private StorageBackendInfo redisBackend() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("correlatorWindowsEnabled", redisProperties.isCorrelatorWindowsEnabled());
        details.put("database", redisProperties.getDatabase());

        if (!redisProperties.isEnabled()) {
            return new StorageBackendInfo(
                    "redis",
                    "redis",
                    "disabled",
                    "redis",
                    null,
                    false,
                    null,
                    null,
                    null,
                    details
            );
        }

        boolean connected = false;
        String connectionError = null;
        if (redisTemplate != null) {
            try {
                String pong = redisTemplate.getConnectionFactory().getConnection().ping();
                connected = "PONG".equalsIgnoreCase(pong);
            } catch (Exception ex) {
                connectionError = ex.getMessage();
            }
        } else {
            connectionError = "Redis template not configured";
        }

        String endpoint = redisProperties.getHost() + ":" + redisProperties.getPort();
        return new StorageBackendInfo(
                "redis",
                "redis",
                "redis",
                "redis",
                endpoint,
                connected,
                connectionError,
                null,
                null,
                details
        );
    }

    private ProbeResult probeJdbc(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return new ProbeResult(connection.isValid(2), null);
        } catch (Exception ex) {
            return new ProbeResult(false, ex.getMessage());
        }
    }

    private String jdbcEndpoint(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikari) {
            return sanitizeJdbcUrl(hikari.getJdbcUrl());
        }
        try (Connection connection = dataSource.getConnection()) {
            return sanitizeJdbcUrl(connection.getMetaData().getURL());
        } catch (Exception ex) {
            return null;
        }
    }

    private ProbeResult probeClickHouse(String url, String database, String username, String password) {
        try {
            HttpResponse<String> response = httpClient.send(
                    buildClickHouseRequest(url + "/ping", "", database, username, password),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() >= 400) {
                return new ProbeResult(false, "HTTP " + response.statusCode());
            }
            return new ProbeResult(true, null);
        } catch (Exception ex) {
            return new ProbeResult(false, ex.getMessage());
        }
    }

    private Long clickHouseCount(String url, String database, String table, String username, String password) {
        try {
            String query = "SELECT count() AS cnt FROM " + database + "." + table + " FORMAT JSONEachRow";
            HttpResponse<String> response = httpClient.send(
                    buildClickHouseRequest(url, query, database, username, password),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() >= 400 || response.body().isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(response.body().lines().findFirst().orElse("{}"));
            return node.path("cnt").asLong();
        } catch (Exception ex) {
            return null;
        }
    }

    private ProbeResult probeCassandra(CassandraStoreProperties config) {
        try (com.ispf.server.cassandra.CassandraClient client = new com.ispf.server.cassandra.CassandraClient(config)) {
            client.queryRows("SELECT release_version FROM system.local LIMIT 1");
            return new ProbeResult(true, null);
        } catch (Exception ex) {
            return new ProbeResult(false, ex.getMessage());
        }
    }

    private HttpRequest buildClickHouseRequest(
            String url,
            String query,
            String database,
            String username,
            String password
    ) {
        StringBuilder target = new StringBuilder(url);
        if (query != null && !query.isBlank()) {
            if (!url.contains("?")) {
                target.append('?');
            } else if (!url.endsWith("&") && !url.endsWith("?")) {
                target.append('&');
            }
            target.append("database=").append(encode(database));
            target.append("&query=").append(encode(query));
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(target.toString()))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (password != null && !password.isBlank()) {
            String user = username != null && !username.isBlank() ? username : "default";
            String credentials = user + ":" + password;
            builder.header("Authorization", "Basic " + java.util.Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return builder.build();
    }

    private static String cassandraEndpoint(CassandraStoreProperties config) {
        return config.getContactPoints() + ":" + config.getPort()
                + " / " + config.getKeyspace()
                + " (dc=" + config.getLocalDatacenter() + ")";
    }

    private static String sanitizeJdbcUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(?i)(password=)[^&]*", "$1***");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record ProbeResult(boolean connected, String error) {
    }

    public record StorageHealth(String timestamp, List<StorageBackendInfo> backends) {
    }

    public record StorageBackendInfo(
            String id,
            String role,
            String store,
            String engine,
            String endpoint,
            boolean connected,
            String connectionError,
            Long recordCount,
            Integer retentionDays,
            Map<String, Object> details
    ) {
    }
}
