package com.ispf.server.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.config.VariableHistoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * High-throughput variable historian backed by ClickHouse MergeTree (HTTP interface).
 */
@Service
@ConditionalOnProperty(name = "ispf.variable-history.store", havingValue = "clickhouse")
public class ClickHouseVariableHistoryStore implements VariableHistoryWriteStore, VariableHistoryQueryStore {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseVariableHistoryStore.class);

    private static final DateTimeFormatter CH_DATETIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC);

    private static final String SELECT_COLUMNS = """
            sampled_at, value_double, value_text
            """;

    private final VariableHistoryProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public ClickHouseVariableHistoryStore(VariableHistoryProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(111)
    public void ensureSchema() {
        VariableHistoryProperties.ClickHouse config = properties.getClickhouse();
        executeStatement("CREATE DATABASE IF NOT EXISTS " + config.getDatabase());
        int retentionDays = properties.getRetentionDays();
        String ttlClause = retentionDays > 0
                ? " TTL toDateTime(sampled_at) + INTERVAL " + retentionDays + " DAY"
                : "";
        executeStatement(String.format("""
                CREATE TABLE IF NOT EXISTS %s.%s (
                    object_path String,
                    variable_name String,
                    field_name String,
                    sampled_at DateTime64(3, 'UTC'),
                    value_double Nullable(Float64),
                    value_text Nullable(String)
                ) ENGINE = MergeTree()
                PARTITION BY toYYYYMM(sampled_at)
                ORDER BY (object_path, variable_name, field_name, sampled_at)%s
                SETTINGS index_granularity = 8192
                """, config.getDatabase(), config.getTable(), ttlClause));
        log.info(
                "ClickHouse variable history ready (database={}, table={}, retentionDays={})",
                config.getDatabase(),
                config.getTable(),
                retentionDays
        );
    }

    @Override
    public void appendBatch(List<VariableHistoryWriteRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        StringJoiner body = new StringJoiner("\n");
        for (VariableHistoryWriteRecord record : records) {
            body.add(toJsonLine(record));
        }
        String insertQuery = "INSERT INTO " + qualifiedTable() + " FORMAT JSONEachRow";
        post(insertQuery, body.toString());
    }

    @Override
    public void appendOne(VariableHistoryWriteRecord record) {
        appendBatch(List.of(record));
    }

    @Override
    public List<VariableHistoryService.VariableHistorySample> query(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit
    ) {
        int cappedLimit = Math.min(Math.max(limit, 1), 10_000);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("objectPath", objectPath);
        params.put("variableName", variableName);
        params.put("fieldName", fieldName);
        params.put("limit", String.valueOf(cappedLimit));

        String sql;
        if (from != null && to != null) {
            params.put("fromTs", CH_DATETIME.format(from));
            params.put("toTs", CH_DATETIME.format(to));
            sql = """
                    SELECT %s FROM %s
                    WHERE object_path = {objectPath:String}
                      AND variable_name = {variableName:String}
                      AND field_name = {fieldName:String}
                      AND sampled_at >= {fromTs:DateTime64(3, 'UTC')}
                      AND sampled_at <= {toTs:DateTime64(3, 'UTC')}
                    ORDER BY sampled_at ASC
                    FORMAT JSONEachRow
                    """.formatted(SELECT_COLUMNS, qualifiedTable());
            List<VariableHistoryService.VariableHistorySample> rows = parseSamples(postQuery(sql, params));
            if (rows.size() > cappedLimit) {
                return rows.subList(rows.size() - cappedLimit, rows.size());
            }
            return rows;
        }

        sql = """
                SELECT %s FROM %s
                WHERE object_path = {objectPath:String}
                  AND variable_name = {variableName:String}
                  AND field_name = {fieldName:String}
                ORDER BY sampled_at DESC
                LIMIT {limit:UInt32}
                FORMAT JSONEachRow
                """.formatted(SELECT_COLUMNS, qualifiedTable());
        List<VariableHistoryService.VariableHistorySample> rows = parseSamples(postQuery(sql, params));
        List<VariableHistoryService.VariableHistorySample> reversed = new ArrayList<>(rows);
        Collections.reverse(reversed);
        return reversed;
    }

    @Override
    public List<VariableHistoryService.VariableHistoryBucket> aggregateBuckets(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            Duration bucket,
            int maxBuckets
    ) {
        long bucketSeconds = bucket.getSeconds();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("objectPath", objectPath);
        params.put("variableName", variableName);
        params.put("fieldName", fieldName);
        params.put("fromTs", CH_DATETIME.format(from));
        params.put("toTs", CH_DATETIME.format(to));
        params.put("bucketSeconds", String.valueOf(bucketSeconds));
        params.put("maxBuckets", String.valueOf(maxBuckets));

        String sql = """
                SELECT
                    toStartOfInterval(sampled_at, toIntervalSecond({bucketSeconds:UInt32})) AS bucket_start,
                    avg(value_double) AS avg_val,
                    min(value_double) AS min_val,
                    max(value_double) AS max_val,
                    count() AS sample_count
                FROM %s
                WHERE object_path = {objectPath:String}
                  AND variable_name = {variableName:String}
                  AND field_name = {fieldName:String}
                  AND sampled_at >= {fromTs:DateTime64(3, 'UTC')}
                  AND sampled_at <= {toTs:DateTime64(3, 'UTC')}
                  AND value_double IS NOT NULL
                GROUP BY bucket_start
                ORDER BY bucket_start ASC
                LIMIT {maxBuckets:UInt32}
                FORMAT JSONEachRow
                """.formatted(qualifiedTable());

        return parseBuckets(postQuery(sql, params));
    }

    @Override
    public boolean supportsApplicationRetentionPurge() {
        return false;
    }

    private List<VariableHistoryService.VariableHistorySample> parseSamples(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<VariableHistoryService.VariableHistorySample> samples = new ArrayList<>();
        for (String line : body.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(line);
                Double value = node.path("value_double").isNull() ? null : node.path("value_double").asDouble();
                String text = node.path("value_text").isNull() ? null : node.path("value_text").asText(null);
                samples.add(new VariableHistoryService.VariableHistorySample(
                        parseInstant(node.path("sampled_at").asText()),
                        value,
                        text
                ));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to parse ClickHouse JSONEachRow line: " + line, ex);
            }
        }
        return samples;
    }

    private List<VariableHistoryService.VariableHistoryBucket> parseBuckets(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<VariableHistoryService.VariableHistoryBucket> buckets = new ArrayList<>();
        for (String line : body.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(line);
                buckets.add(new VariableHistoryService.VariableHistoryBucket(
                        parseInstant(node.path("bucket_start").asText()),
                        node.path("avg_val").isNull() ? null : node.path("avg_val").asDouble(),
                        node.path("min_val").isNull() ? null : node.path("min_val").asDouble(),
                        node.path("max_val").isNull() ? null : node.path("max_val").asDouble(),
                        node.path("sample_count").asInt(0)
                ));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to parse ClickHouse JSONEachRow line: " + line, ex);
            }
        }
        return buckets;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        if (value.contains("T")) {
            return Instant.parse(value.endsWith("Z") ? value : value + "Z");
        }
        return CH_DATETIME.parse(value, Instant::from);
    }

    private String toJsonLine(VariableHistoryWriteRecord record) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("object_path", record.objectPath());
            row.put("variable_name", record.variableName());
            row.put("field_name", record.fieldName());
            row.put("sampled_at", CH_DATETIME.format(record.sampledAt()));
            row.put("value_double", record.valueDouble());
            row.put("value_text", record.valueText());
            return objectMapper.writeValueAsString(row);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize variable history record", ex);
        }
    }

    private void executeStatement(String sql) {
        post(sql, "", "default");
    }

    private void post(String query, String body) {
        post(query, body, properties.getClickhouse().getDatabase());
    }

    private void post(String query, String body, String database) {
        HttpResponse<String> response = send(buildRequest(query, body, Map.of(), database));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "ClickHouse request failed (" + response.statusCode() + "): " + response.body()
            );
        }
    }

    private String postQuery(String query, Map<String, String> params) {
        HttpResponse<String> response = send(buildRequest(query, "", params, properties.getClickhouse().getDatabase()));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "ClickHouse query failed (" + response.statusCode() + "): " + response.body()
            );
        }
        return response.body();
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

    private HttpRequest buildRequest(String query, String body, Map<String, String> params, String database) {
        VariableHistoryProperties.ClickHouse config = properties.getClickhouse();
        StringBuilder url = new StringBuilder(config.getUrl());
        if (!config.getUrl().contains("?")) {
            url.append('?');
        } else if (!config.getUrl().endsWith("&") && !config.getUrl().endsWith("?")) {
            url.append('&');
        }
        url.append("database=").append(encode(database));
        url.append("&query=").append(encode(query));
        for (Map.Entry<String, String> param : params.entrySet()) {
            url.append("&param_").append(encode(param.getKey())).append('=').append(encode(param.getValue()));
        }
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
        VariableHistoryProperties.ClickHouse config = properties.getClickhouse();
        return config.getDatabase() + "." + config.getTable();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
