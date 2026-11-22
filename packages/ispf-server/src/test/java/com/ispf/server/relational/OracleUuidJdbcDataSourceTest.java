package com.ispf.server.relational;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OracleUuidJdbcDataSourceTest {

    @Test
    void bindsUuidsAsTextAndPassesOtherValuesThrough() throws Exception {
        PreparedStatement target = mock(PreparedStatement.class);
        Connection connection = new OracleUuidJdbcDataSource(dataSource(target)).getConnection();
        UUID id = UUID.fromString("7d444840-9dc0-11d1-b245-5ffdce74fad2");

        PreparedStatement statement = connection.prepareStatement("INSERT INTO t (id, n) VALUES (?, ?)");
        statement.setObject(1, id);
        statement.setObject(2, 42);
        statement.setObject(3, id, Types.OTHER);

        verify(target).setString(1, "7d444840-9dc0-11d1-b245-5ffdce74fad2");
        verify(target).setObject(2, 42);
        verify(target).setString(3, "7d444840-9dc0-11d1-b245-5ffdce74fad2");
        verify(target, never()).setObject(1, id);
        assertThat(statement.getConnection()).isSameAs(connection);
    }

    @Test
    void readsUuidsFromTextColumns() throws Exception {
        PreparedStatement target = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(target.executeQuery()).thenReturn(rows);
        when(rows.getString(1)).thenReturn("7d444840-9dc0-11d1-b245-5ffdce74fad2");
        when(rows.getString("id")).thenReturn(null);
        when(rows.getString("name")).thenReturn("n");

        ResultSet resultSet = new OracleUuidJdbcDataSource(dataSource(target)).getConnection()
                .prepareStatement("SELECT id FROM t").executeQuery();

        assertThat(resultSet.getObject(1, UUID.class)).isEqualTo(UUID.fromString("7d444840-9dc0-11d1-b245-5ffdce74fad2"));
        assertThat(resultSet.getObject("id", UUID.class)).isNull();
        assertThat(resultSet.getString("name")).isEqualTo("n");
    }

    @Test
    void wrapsOnlyThePrimaryDataSourceOfAnOracleDeployment() {
        DataSource raw = mock(DataSource.class);
        BeanPostProcessor oracle = RelationalConfiguration.oracleUuidJdbcDataSource(
                new MockEnvironment().withProperty("ispf.metadata.db.kind", "oracle"));
        assertThat(oracle.postProcessAfterInitialization(raw, "dataSource")).isInstanceOf(OracleUuidJdbcDataSource.class);
        assertThat(oracle.postProcessAfterInitialization(raw, "clickHouseDataSource")).isSameAs(raw);

        BeanPostProcessor postgres = RelationalConfiguration.oracleUuidJdbcDataSource(
                new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db/ispf"));
        assertThat(postgres.postProcessAfterInitialization(raw, "dataSource")).isSameAs(raw);
    }

    private static DataSource dataSource(PreparedStatement statement) throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }
}
