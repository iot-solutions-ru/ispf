package com.ispf.server.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.config.HistorianTierProfile;
import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.storage.ClickHouseSchemaBootstrap;
import com.ispf.server.storage.StorageStartupRetry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Write-only warm-tier ClickHouse appends for historian tier routing (BL-159).
 */
@Service
@ConditionalOnProperty(name = "ispf.historian.tiers.warm.enabled", havingValue = "true")
@ConditionalOnExpression("'${ispf.variable-history.store:jdbc}' != 'clickhouse' && '${ispf.variable-history.store:jdbc}' != 'cassandra' && '${ispf.variable-history.store:jdbc}' != 'scylla'")
class ClickHouseWarmTierWriteStore implements VariableHistoryWriteStore {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseWarmTierWriteStore.class);

    private static final DateTimeFormatter CH_DATETIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC);

    private final HistorianTierProfile warmTier;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final boolean configured;

    ClickHouseWarmTierWriteStore(HistorianTierProperties tierProperties) {
        this.warmTier = tierProperties.warmTier();
        this.configured = warmTier.isEnabled()
                && warmTier.isClickHouseStore()
                && warmTier.getClickhouse().getUrl() != null
                && !warmTier.getClickhouse().getUrl().isBlank();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    boolean isConfigured() {
        return configured;
    }

    @PostConstruct
    public void ensureSchema() {
        if (!configured) {
            return;
        }
        var config = warmTier.getClickhouse();
        String database = ClickHouseSchemaBootstrap.requireValidIdentifier(config.getDatabase(), "database");
        String table = ClickHouseSchemaBootstrap.requireValidIdentifier(config.getTable(), "table");
        String qualifiedTable = database + "." + table;
        StorageStartupRetry.run("ClickHouse warm-tier historian write", () -> {
            executeStatement("CREATE DATABASE IF NOT EXISTS " + database);
            int retentionDays = warmTier.getRetentionDays();
            String ttlClause = retentionDays > 0
                    ? " TTL toDateTime(sampled_at) + INTERVAL " + retentionDays + " DAY"
                    : "";
            executeStatement(String.format("""
                    CREATE TABLE IF NOT EXISTS %s (
                        object_path String,
                        variable_name String,
                        field_name String,
                        sampled_at DateTime64(3, 'UTC'),
                        observed_at DateTime64(3, 'UTC'),
                        value_double Nullable(Float64),
                        value_text Nullable(String)
                    ) ENGINE = MergeTree()
                    PARTITION BY toYYYYMM(sampled_at)
                    ORDER BY (object_path, variable_name, field_name, sampled_at)%s
                    SETTINGS index_granularity = 8192
                    """, qualifiedTable, ttlClause));
        });
        log.info("ClickHouse warm-tier write ready (database={}, table={})", database, table);
    }

    @Override
    public void appendBatch(List<VariableHistoryWriteRecord> records) {
        if (!configured || records.isEmpty()) {
            return;
        }
        StringJoiner body = new StringJoiner("\n");
        for (VariableHistoryWriteRecord record : records) {
            body.add(toJsonLine(record));
        }
        post("INSERT INTO " + qualifiedTable() + " FORMAT JSONEachRow", body.toString());
    }

    @Override
    public void appendOne(VariableHistoryWriteRecord record) {
        appendBatch(List.of(record));
    }

    void appendBestEffort(List<VariableHistoryWriteRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        try {
            appendBatch(records);
        } catch (RuntimeException ex) {
            log.warn("Warm-tier ClickHouse append failed ({} records): {}", records.size(), ex.getMessage());
        }
    }

    private String toJsonLine(VariableHistoryWriteRecord record) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("object_path", record.objectPath());
            row.put("variable_name", record.variableName());
            row.put("field_name", record.fieldName());
            row.put("sampled_at", CH_DATETIME.format(record.sampledAt()));
            row.put("observed_at", CH_DATETIME.format(record.observedAt()));
            row.put("value_double", record.valueDouble());
            row.put("value_text", record.valueText());
            return objectMapper.writeValueAsString(row);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize variable history record", ex);
        }
    }

    private void executeStatement(String sql) {
        post(sql, "");
    }

    private void post(String query, String body) {
        HttpResponse<String> response = send(buildRequest(query, body));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "ClickHouse request failed (" + response.statusCode() + "): " + response.body()
            );
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ClickHouse request interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("ClickHouse request failed", ex);
        }
    }

    private HttpRequest buildRequest(String query, String body) {
        var config = warmTier.getClickhouse();
        StringBuilder url = new StringBuilder(config.getUrl());
        if (!config.getUrl().contains("?")) {
            url.append('?');
        } else if (!config.getUrl().endsWith("&") && !config.getUrl().endsWith("?")) {
            url.append('&');
        }
        url.append("database=").append(encode(config.getDatabase()));
        url.append("&query=").append(encode(query));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            String username = config.getUsername() != null && !config.getUsername().isBlank()
                    ? config.getUsername()
                    : "default";
            String credentials = username + ":" + config.getPassword();
            builder.header("Authorization", "Basic " + java.util.Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return builder.build();
    }

    private String qualifiedTable() {
        var config = warmTier.getClickhouse();
        return config.getDatabase() + "." + config.getTable();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
