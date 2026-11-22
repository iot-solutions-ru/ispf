package com.ispf.server.platform;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.relational.MssqlDialect;
import com.ispf.server.relational.MysqlDialect;
import com.ispf.server.relational.OracleDialect;
import com.ispf.server.relational.PostgreSqlDialect;
import com.ispf.server.relational.RelationalDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformJobServiceDialectSqlTest {

    @Mock
    JdbcTemplate jdbcTemplate;
    @Mock
    ClusterProperties clusterProperties;
    @Mock
    NatsProperties natsProperties;
    @Captor
    ArgumentCaptor<String> sqlCaptor;
    @Captor
    ArgumentCaptor<RowMapper<UUID>> jobIdMapperCaptor;
    @Captor
    ArgumentCaptor<PreparedStatementSetter> claimSetterCaptor;

    @Test
    void runningJobsSortNullsLastWithoutNullsLastOnMysql() {
        assertThat(runningJobsSql(new MysqlDialect()))
                .contains("ORDER BY started_at IS NULL, started_at, created_at")
                .doesNotContain("NULLS");
    }

    @Test
    void runningJobsSortNullsLastWithCaseOnMssql() {
        assertThat(runningJobsSql(new MssqlDialect()))
                .contains("ORDER BY CASE WHEN started_at IS NULL THEN 1 ELSE 0 END, started_at, created_at")
                .doesNotContain("NULLS");
    }

    @Test
    void runningJobsKeepNullsLastOnPostgres() {
        assertThat(runningJobsSql(new PostgreSqlDialect()))
                .contains("ORDER BY started_at NULLS LAST, created_at");
    }

    @Test
    void claimFetchesOneRowSoSkipLockedLocksOneJob() throws Exception {
        service(new OracleDialect()).claimNextJob();
        verify(jdbcTemplate).query(sqlCaptor.capture(), claimSetterCaptor.capture(), any(RowMapper.class));
        // Oracle's claim has no row limit; the one-row fetch is what keeps SKIP LOCKED to one job.
        assertThat(sqlCaptor.getValue()).contains("FOR UPDATE SKIP LOCKED").doesNotContain("FETCH");

        PreparedStatement select = mock(PreparedStatement.class);
        claimSetterCaptor.getValue().setValues(select);
        verify(select).setMaxRows(1);
        verify(select).setFetchSize(1);
        verify(select).setString(1, "QUEUED");
    }

    @Test
    void claimReadsJobIdThatTheDriverReturnsAsString() throws Exception {
        service(new MysqlDialect()).claimNextJob();
        verify(jdbcTemplate).query(anyString(), claimSetterCaptor.capture(), jobIdMapperCaptor.capture());

        UUID jobId = UUID.randomUUID();
        ResultSet rs = mock(ResultSet.class);
        // MariaDB Connector/J: getObject on a CHAR(36) column is a String, not a UUID.
        lenient().when(rs.getObject("job_id")).thenReturn(jobId.toString());
        when(rs.getString("job_id")).thenReturn(jobId.toString());

        assertThat(jobIdMapperCaptor.getValue().mapRow(rs, 0)).isEqualTo(jobId);
    }

    private String runningJobsSql(RelationalDialect dialect) {
        service(dialect).listRunningByHolder("replica-a");
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq("RUNNING"), eq("replica-a"));
        return sqlCaptor.getValue().replaceAll("\\s+", " ");
    }

    private PlatformJobService service(RelationalDialect dialect) {
        when(natsProperties.replicaId()).thenReturn("replica-a");
        return new PlatformJobService(jdbcTemplate, new ObjectMapper(), clusterProperties, natsProperties, dialect);
    }
}
