package com.ispf.server.relational;

import com.ispf.server.application.binding.ApplicationSqlBindingStore;
import com.ispf.server.application.binding.ApplicationSqlBindingStore.SqlBinding;
import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.platform.PlatformJobService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform SQL that the bundle gates do not reach and that held the PostgreSQL-only SQL found under
 * P-MYSQL-PG-SQL: the job queue ({@code job_id} read, running list ordered NULLs last) and
 * function-success SQL-binding triggers (string concat). Runs against a migrated {@code ISPF_DB}.
 */
final class PlatformSqlPathsTck {

    private PlatformSqlPathsTck() {
    }

    static void jobQueueRoundTrip(
            JdbcTemplate jdbc,
            RelationalDialect dialect,
            ClusterProperties cluster,
            NatsProperties nats
    ) {
        PlatformJobService jobs = new PlatformJobService(jdbc, new ObjectMapper(), cluster, nats, dialect);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));

        UUID queued = jobs.submit("tck_probe", Map.of("probe", "value"), "tck");
        PlatformJobService.ClaimedJob claimed = tx.execute(status -> jobs.claimNextJob()).orElseThrow();
        assertThat(claimed.jobId()).isEqualTo(queued);
        assertThat(claimed.payload()).containsEntry("probe", "value");

        // Older than the claimed job, so only NULLs-last ordering puts it second.
        UUID unstarted = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO platform_jobs (job_id, job_type, status, priority, payload, created_at, holder_id)
                        VALUES (?, 'tck_probe', 'RUNNING', 0, '{}', ?, ?)
                        """,
                unstarted.toString(), Timestamp.from(Instant.now().minusSeconds(60)), jobs.instanceId()
        );
        assertThat(jobs.listRunningByHolder(jobs.instanceId()))
                .extracting(PlatformJobService.JobView::jobId)
                .containsExactly(queued, unstarted);

        jobs.completeJob(queued, Map.of("ok", true));
        assertThat(jobs.getJob(queued).orElseThrow().status()).isEqualTo("COMPLETED");
    }

    static void functionSuccessTriggers(JdbcTemplate jdbc, RelationalDialect dialect) {
        String app = "tck-sql-bindings";
        String hub = "root.platform.singletons.tck-hub";
        jdbc.update("INSERT INTO applications (app_id, display_name) VALUES (?, ?)", app, "TCK SQL bindings");
        ApplicationSqlBindingStore store = new ApplicationSqlBindingStore(jdbc, new PlatformSqlCatalog(dialect), dialect);

        UUID single = upsert(store, app, "single", hub, "recalc");
        UUID list = upsert(store, app, "list", hub, "recalc,reset");
        UUID any = upsert(store, app, "any", null, null);

        assertThat(ids(store.listForFunctionSuccess(app, hub, "recalc"))).containsExactlyInAnyOrder(single, list, any);
        assertThat(ids(store.listForFunctionSuccess(app, hub, "reset"))).containsExactlyInAnyOrder(list, any);
        // MySQL's default sql_mode reads || as OR, which matched every named trigger here.
        assertThat(ids(store.listForFunctionSuccess(app, hub, "other"))).containsExactly(any);
    }

    private static UUID upsert(
            ApplicationSqlBindingStore store,
            String app,
            String variable,
            String triggerObjectPath,
            String triggerFunctionName
    ) {
        UUID id = UUID.randomUUID();
        store.upsert(new SqlBinding(
                id, app, "root.platform.singletons.tck-target", variable, "SELECT 1 AS value",
                "on_function_success", null, "value", triggerObjectPath, triggerFunctionName, true, null
        ));
        return id;
    }

    private static List<UUID> ids(List<SqlBinding> bindings) {
        return bindings.stream().map(SqlBinding::id).toList();
    }
}
