package com.ispf.server.history;

import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.platform.AutomationMetricsRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.clickhouse.ClickHouseContainer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 1 historian TCK: disk WAL catch-up after ClickHouse secondary outage and batch ingest throughput.
 *
 * <p>Skips cleanly when Docker is unavailable. Run:
 * {@code ./gradlew :packages:ispf-server:test --tests com.ispf.server.history.HistorianCatchUpClickHouseTckTest}
 */
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class HistorianCatchUpClickHouseTckTest {

  @TempDir
  Path walDir;

  @Test
  void secondaryOutageCatchUpReplayMatchesWalWithoutDuplicates() throws Exception {
    assumeDocker();

    try (ClickHouseContainer clickhouse = clickHouseContainer()) {
      clickhouse.start();

      VariableHistoryProperties properties = clickHouseProperties(clickhouse);
      FlakyClickHouseSecondary secondary = new FlakyClickHouseSecondary(properties);
      secondary.ensureSchema();
      secondary.simulateOutage = true;

      @SuppressWarnings("unchecked")
      ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondaryProvider =
          mock(ObjectProvider.class);
      when(secondaryProvider.getIfAvailable()).thenReturn(secondary);

      HistorianRangeCatchUpService catchUp = new HistorianRangeCatchUpService(
          new HistorianSampleWal(walDir, 64L * 1024 * 1024),
          new MemoryRanges(),
          secondaryProvider
      );

      JdbcVariableHistoryWriteStore primary = mock(JdbcVariableHistoryWriteStore.class);
      DualWriteVariableHistoryWriteStore dualWrite = new DualWriteVariableHistoryWriteStore(
          primary,
          secondary,
          new AutomationMetricsRecorder(Optional.of(new SimpleMeterRegistry())),
          HistorianAheadSet.DEFAULT_MAX_KEYS,
          catchUp
      );

      Instant t0 = Instant.parse("2026-11-12T08:00:00Z");
      List<VariableHistoryWriteRecord> outageBatch = List.of(
          sample(t0, 1.0),
          sample(t0.plusSeconds(1), 2.0),
          sample(t0.plusSeconds(2), 3.0)
      );
      dualWrite.appendBatch(outageBatch);

      assertEquals(0, scalarLong(clickhouse, "SELECT count() FROM ispf.variable_samples"));
      assertEquals(0, catchUp.replay(), "replay while CH still down should not copy");

      secondary.simulateOutage = false;
      assertEquals(3, catchUp.replay());
      assertEquals(3, scalarLong(clickhouse, "SELECT count() FROM ispf.variable_samples"));
      assertEquals(
          0,
          scalarLong(clickhouse, """
              SELECT count() FROM (
                SELECT object_path, variable_name, field_name, sampled_at, value_double
                FROM ispf.variable_samples
                GROUP BY object_path, variable_name, field_name, sampled_at, value_double
                HAVING count() > 1
              )
              """)
      );

      List<Double> values = selectValues(clickhouse);
      assertEquals(List.of(1.0, 2.0, 3.0), values);

      assertEquals(0, catchUp.replay(), "second replay must be idempotent");
      assertEquals(3, scalarLong(clickhouse, "SELECT count() FROM ispf.variable_samples"));
    }
  }

  @Test
  void batchIngest100kMeetsMinimumThroughput() throws Exception {
    assumeDocker();

    try (ClickHouseContainer clickhouse = clickHouseContainer()) {
      clickhouse.start();

      VariableHistoryProperties properties = clickHouseProperties(clickhouse);
      ClickHouseVariableHistoryStore store = new ClickHouseVariableHistoryStore(properties);
      store.ensureSchema();

      Instant start = Instant.parse("2026-11-12T00:00:00Z");
      int total = 100_000;
      int chunk = 2_000;
      List<VariableHistoryWriteRecord> buffer = new ArrayList<>(chunk);
      long began = System.nanoTime();
      for (int i = 0; i < total; i++) {
        buffer.add(sample(start.plusMillis(i), i));
        if (buffer.size() == chunk || i == total - 1) {
          store.appendBatch(buffer);
          buffer.clear();
        }
      }
      long elapsedNanos = System.nanoTime() - began;
      double seconds = elapsedNanos / 1_000_000_000.0;
      double rate = total / seconds;

      assertEquals(total, scalarLong(clickhouse, "SELECT count() FROM ispf.variable_samples"));
      assertTrue(
          rate >= 20_000,
          String.format("ingest rate rows/s=%.0f (elapsed %.2fs, rows=%d)", rate, seconds, total)
      );
    }
  }

  private static void assumeDocker() {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker not available; skipping ClickHouse historian catch-up TCK"
    );
  }

  private static ClickHouseContainer clickHouseContainer() {
    return new ClickHouseContainer("clickhouse/clickhouse-server:24.8")
        .withStartupTimeout(Duration.ofMinutes(2));
  }

  private static VariableHistoryProperties clickHouseProperties(ClickHouseContainer clickhouse) {
    VariableHistoryProperties properties = new VariableHistoryProperties();
    properties.setStore("clickhouse");
    properties.getClickhouse().setUrl(clickhouse.getHttpUrl());
    properties.getClickhouse().setUsername(clickhouse.getUsername());
    properties.getClickhouse().setPassword(clickhouse.getPassword());
    properties.getClickhouse().setDatabase("ispf");
    properties.getClickhouse().setTable("variable_samples");
    return properties;
  }

  private static VariableHistoryWriteRecord sample(Instant at, double value) {
    return VariableHistoryWriteRecord.ingested(
        "root.platform.devices.catchup",
        "metric",
        "value",
        at,
        value,
        null,
        "GOOD"
    );
  }

  private static long scalarLong(ClickHouseContainer clickhouse, String sql) throws Exception {
    String body = query(clickhouse, sql + " FORMAT TabSeparated");
    return Long.parseLong(body.trim());
  }

  private static List<Double> selectValues(ClickHouseContainer clickhouse) throws Exception {
    String body = query(
        clickhouse,
        "SELECT value_double FROM ispf.variable_samples ORDER BY sampled_at FORMAT TabSeparated"
    );
    if (body.isBlank()) {
      return List.of();
    }
    return body.lines().map(Double::parseDouble).toList();
  }

  private static String query(ClickHouseContainer clickhouse, String sql) throws Exception {
    String url = clickhouse.getHttpUrl()
        + "/?database=ispf"
        + "&user=" + URLEncoder.encode(clickhouse.getUsername(), StandardCharsets.UTF_8)
        + "&password=" + URLEncoder.encode(clickhouse.getPassword(), StandardCharsets.UTF_8)
        + "&query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8);
    try (HttpClient http = HttpClient.newHttpClient()) {
      HttpResponse<String> response = http.send(
          HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(60)).build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
      );
      assertEquals(200, response.statusCode(), response.body());
      return response.body();
    }
  }

  private static final class FlakyClickHouseSecondary extends ClickHouseVariableHistorySecondaryWriteStore {
    volatile boolean simulateOutage;

    FlakyClickHouseSecondary(VariableHistoryProperties properties) {
      super(properties);
    }

    @Override
    public void appendBatch(List<VariableHistoryWriteRecord> records) {
      if (simulateOutage) {
        throw new IllegalStateException("simulated ClickHouse outage");
      }
      super.appendBatch(records);
    }
  }

  private static final class MemoryRanges implements HistorianCatchUpRangeStore {
    private final List<Range> ranges = new ArrayList<>();

    @Override
    public synchronized void cover(String seriesKey, Instant from, Instant to) {
      ranges.add(new Range(seriesKey, from, to));
    }

    @Override
    public synchronized List<Range> list() {
      return List.copyOf(ranges);
    }

    @Override
    public synchronized void remove(String seriesKey, Instant from, Instant to) {
      ranges.removeIf(range -> range.seriesKey().equals(seriesKey)
          && range.from().equals(from)
          && range.to().equals(to));
    }
  }
}
