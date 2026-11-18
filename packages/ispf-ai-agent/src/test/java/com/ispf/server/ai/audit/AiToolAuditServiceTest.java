package com.ispf.server.ai.audit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiToolAuditServiceTest {

    @Test
    void longToolNameIsClippedToTheColumn() {
        AiToolAuditStore store = mock(AiToolAuditStore.class);
        when(store.insert(any())).thenReturn(7L);
        String garbage = "finish\",\"arguments\":{\"summary\":\"" + "x".repeat(300) + "\"}";

        long id = new AiToolAuditService(store).record(garbage, null, "admin", "{}", "OK", null, null, null, List.of());

        ArgumentCaptor<AiToolAuditStore.AuditEntry> entry = ArgumentCaptor.forClass(AiToolAuditStore.AuditEntry.class);
        verify(store).insert(entry.capture());
        assertThat(id).isEqualTo(7L);
        assertThat(entry.getValue().toolName()).hasSize(64);
    }

    @Test
    void databaseFailureDoesNotFailTheCaller() {
        AiToolAuditStore store = mock(AiToolAuditStore.class);
        when(store.insert(any())).thenThrow(new DataIntegrityViolationException("value too long"));

        long id = new AiToolAuditService(store).record("list_objects", null, "admin", "{}", "OK", null, null, null, List.of());

        assertThat(id).isEqualTo(-1L);
    }
}
