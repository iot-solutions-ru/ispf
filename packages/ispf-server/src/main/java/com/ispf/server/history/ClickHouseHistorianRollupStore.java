package com.ispf.server.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.storage.ClickHouseSchemaBootstrap;
import com.ispf.server.storage.StorageStartupRetry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * ClickHouse storage for pre-materialized historian rollups (BL-205).
 */
@Service
public class ClickHouseHistorianRollupStore {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseHistorianRollupStore.class);

    private static final DateTimeFormatter CH_DATETIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC);

    private final VariableHistoryProperties historyProperties;
    private final HistorianTierProperties tierProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final ClickHouseVariableHistoryReader rawReader;
    private final boolean configured;
    private final String qualifiedRollupTable;

    public ClickHouseHistorianRollupStore(
            VariableHistoryProperties historyProperties,
            HistorianTierProperties tierProperties
    ) {
        this.historyProperties = historyProperties;
        this.tierProperties = tierProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        Optional<VariableHistoryProperties.ClickHouse> clickhouse = resolveClickHouse();
        this.configured = clickhouse.isPresent();
        if (configured) {
            VariableHistoryProperties.ClickHouse config = clickhouse.orElseThrow();
            this.rawReader = new ClickHouseVariableHistoryReader(config);
            String database = ClickHouseSchemaBootstrap.requireValidIdentifier(config.getDatabase(), "database");
            String rollupTable = ClickHouseSchemaBootstrap.requireValidIdentifier(
                    config.getRollupTable() != null && !config.getRollupTable().isBlank()
                            ? config.getRollupTable()
                            : "variable_rollups",
                    "rollupTable"
            );
            this.qualifiedRollupTable = database + "." + rollupTable;
        } else {
            this.rawReader = null;
            this.qualifiedRollupTable = "";
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    @PostConstruct
    public void ensureSchema() {
        if (!configured) {
            return;
        }
        VariableHistoryProperties.ClickHouse config = resolveClickHouse().orElseThrow();
        String database = ClickHouseSchemaBootstrap.requireValidIdentifier(config.getDatabase(), "database");
        StorageStartupRetry.run("ClickHouse historian rollups", () -> {
            executeStatement("CREATE DATABASE IF NOT EXISTS " + database);
            executeStatement(String.format("""
                    CREATE TABLE IF NOT EXISTS %s (
                        object_path String,
                        variable_name String,
                        field_name String,
                        bucket_width_sec UInt32,
                        bucket_start DateTime64(3, 'UTC'),
                        avg_val Nullable(Float64),
                        min_val Nullable(Float64),
                        max_val Nullable(Float64),
                        sample_count UInt32,
                        materialized_at DateTime64(3, 'UTC')
                    ) ENGINE = ReplacingMergeTree(materialized_at)
                    ORDER BY (object_path, variable_name, field_name, bucket_width_sec, bucket_start)
                    SETTINGS index_granularity = 8192
                    """, qualifiedRollupTable));
        });
        log.info("ClickHouse historian rollups ready (table={})", qualifiedRollupTable);
    }

    public List<VariableHistoryService.VariableHistoryBucket> queryBuckets(
            String objectPath,
            String variableName,
            String fieldName,
            Duration bucket,
            Instant from,
            Instant to,
            int maxBuckets
    ) {
        requireConfigured();
        long bucketSeconds = bucket.getSeconds();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("objectPath", objectPath);
        params.put("variableName", variableName);
        params.put("fieldName", fieldName);
        params.put("bucketSeconds", String.valueOf(bucketSeconds));
        params.put("fromTs", CH_DATETIME.format(from));
        params.put("toTs", CH_DATETIME.format(to));
        params.put("maxBuckets", String.valueOf(maxBuckets));

        String sql = """
                SELECT
                    bucket_start,
                    avg_val,
                    min_val,
                    max_val,
                    sample_count
                FROM %s FINAL
                WHERE object_path = {objectPath:String}
                  AND variable_name = {variableName:String}
                  AND field_name = {fieldName:String}
                  AND bucket_width_sec = {bucketSeconds:UInt32}
                  AND bucket_start >= {fromTs:DateTime64(3, 'UTC')}
                  AND bucket_start <= {toTs:DateTime64(3, 'UTC')}
                ORDER BY bucket_start ASC
                LIMIT {maxBuckets:UInt32}
                FORMAT JSONEachRow
                """.formatted(qualifiedRollupTable);

        return parseBuckets(postQuery(sql, params));
    }

    public Optional<Instant> maxMaterializedBucketStart(
            String objectPath,
            String variableName,
            String fieldName,
            Duration bucket
    ) {
        requireConfigured();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("objectPath", objectPath);
        params.put("variableName", variableName);
        params.put("fieldName", fieldName);
        params.put("bucketSeconds", String.valueOf(bucket.getSeconds()));

        String sql = """
                SELECT max(bucket_start) AS max_bucket
                FROM %s FINAL
                WHERE object_path = {objectPath:String}
                  AND variable_name = {variableName:String}
                  AND field_name = {fieldName:String}
                  AND bucket_width_sec = {bucketSeconds:UInt32}
                FORMAT JSONEachRow
                """.formatted(qualifiedRollupTable);

        String body = postQuery(sql, params);
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(body.lines().findFirst().orElse(""));
            if (node.path("max_bucket").isNull() || node.path("max_bucket").asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parseInstant(node.path("max_bucket").asText()));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse max bucket response", ex);
        }
    }

    public int materializeRange(
            HistorianRollupSubscription subscription,
            Instant from,
            Instant to,
            int maxBuckets
    ) {
        requireConfigured();
        List<VariableHistoryService.VariableHistoryBucket> buckets = rawReader.aggregateBuckets(
                subscription.objectPath(),
                subscription.variableName(),
                subscription.fieldName(),
                from,
                to,
                subscription.bucket(),
                maxBuckets
        );
        if (buckets.isEmpty()) {
            return 0;
        }
        insertBuckets(subscription, buckets);
        return buckets.size();
    }

    public void insertBuckets(
            HistorianRollupSubscription subscription,
            List<VariableHistoryService.VariableHistoryBucket> buckets
    ) {
        requireConfigured();
        if (buckets.isEmpty()) {
            return;
        }
        Instant materializedAt = Instant.now();
        StringJoiner body = new StringJoiner("\n");
        for (VariableHistoryService.VariableHistoryBucket bucket : buckets) {
            body.add(toJsonLine(subscription, bucket, materializedAt));
        }
        String insertQuery = "INSERT INTO " + qualifiedRollupTable + " FORMAT JSONEachRow";
        post(insertQuery, body.toString());
    }

    public void deleteRange(
            HistorianRollupSubscription subscription,
            Instant from,
            Instant to
    ) {
        requireConfigured();
        String sql = """
                ALTER TABLE %s DELETE WHERE object_path = {objectPath:String}
                  AND variable_name = {variableName:String}
                  AND field_name = {fieldName:String}
                  AND bucket_width_sec = {bucketSeconds:UInt32}
                  AND bucket_start >= {fromTs:DateTime64(3, 'UTC')}
                  AND bucket_start <= {toTs:DateTime64(3, 'UTC')}
                """.formatted(qualifiedRollupTable);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("objectPath", subscription.objectPath());
        params.put("variableName", subscription.variableName());
        params.put("fieldName", subscription.fieldName());
        params.put("bucketSeconds", String.valueOf(subscription.bucketWidthSec()));
        params.put("fromTs", CH_DATETIME.format(from));
        params.put("toTs", CH_DATETIME.format(to));
        post(sql, "", params);
    }

    private String toJsonLine(
            HistorianRollupSubscription subscription,
            VariableHistoryService.VariableHistoryBucket bucket,
            Instant materializedAt
    ) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("object_path", subscription.objectPath());
            row.put("variable_name", subscription.variableName());
            row.put("field_name", subscription.fieldName());
            row.put("bucket_width_sec", subscription.bucketWidthSec());
            row.put("bucket_start", CH_DATETIME.format(bucket.ts()));
            row.put("avg_val", bucket.avg());
            row.put("min_val", bucket.min());
            row.put("max_val", bucket.max());
            row.put("sample_count", bucket.count());
            row.put("materialized_at", CH_DATETIME.format(materializedAt));
            return objectMapper.writeValueAsString(row);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to encode rollup row", ex);
        }
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
                throw new IllegalStateException("Failed to parse rollup JSONEachRow line: " + line, ex);
            }
        }
        return buckets;
    }

    private Optional<VariableHistoryProperties.ClickHouse> resolveClickHouse() {
        if ("clickhouse".equalsIgnoreCase(historyProperties.getStore())) {
            VariableHistoryProperties.ClickHouse config = historyProperties.getClickhouse();
            if (config.getUrl() != null && !config.getUrl().isBlank()) {
                return Optional.of(config);
            }
        }
        var warm = tierProperties.warmTier();
        if (warm.isEnabled() && warm.isClickHouseStore()) {
            VariableHistoryProperties.ClickHouse config = warm.getClickhouse();
            if (config.getUrl() != null && !config.getUrl().isBlank()) {
                return Optional.of(config);
            }
        }
        return Optional.empty();
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("ClickHouse historian rollups are not configured");
        }
    }

    private void executeStatement(String statement) {
        post(statement, "");
    }

    private String postQuery(String query, Map<String, String> params) {
        HttpResponse<String> response = send(buildRequest(query, "", params));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "ClickHouse rollup query failed (" + response.statusCode() + "): " + response.body()
            );
        }
        return response.body();
    }

    private void post(String query, String body) {
        post(query, body, Map.of());
    }

    private void post(String query, String body, Map<String, String> params) {
        HttpResponse<String> response = send(buildRequest(query, body, params));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "ClickHouse rollup request failed (" + response.statusCode() + "): " + response.body()
            );
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ClickHouse rollup request interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("ClickHouse rollup request failed", ex);
        }
    }

    private HttpRequest buildRequest(String query, String body, Map<String, String> params) {
        VariableHistoryProperties.ClickHouse config = resolveClickHouse().orElseThrow();
        StringBuilder url = new StringBuilder(config.getUrl());
        if (!config.getUrl().contains("?")) {
            url.append('?');
        } else if (!config.getUrl().endsWith("&") && !config.getUrl().endsWith("?")) {
            url.append('&');
        }
        url.append("database=").append(encode(config.getDatabase()));
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

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        if (value.contains("T")) {
            return Instant.parse(value.endsWith("Z") ? value : value + "Z");
        }
        return CH_DATETIME.parse(value, Instant::from);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
