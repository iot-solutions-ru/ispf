package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingPeriodicScheduleRegistryObjectPathsTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void objectPathsWithBindingRulesJoinsObjectNodes() {
        BindingPeriodicScheduleRegistry registry = new BindingPeriodicScheduleRegistry(jdbcTemplate);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.queryForList(sqlCaptor.capture(), eq(String.class))).thenReturn(List.of());

        registry.objectPathsWithBindingRules();

        String sql = sqlCaptor.getValue().replaceAll("\\s+", " ").toLowerCase();
        assertThat(sql).contains("join object_nodes");
        assertThat(sql).contains("n.path = v.object_path");
        verify(jdbcTemplate).queryForList(sqlCaptor.getValue(), String.class);
    }
}
