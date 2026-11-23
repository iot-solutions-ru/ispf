package com.ispf.server.audit;

import com.ispf.server.persistence.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceLimitTest {

    @Mock
    private AuditEventRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuditSiemWebhookDispatcher siemWebhookDispatcher;

    @Mock
    private AuditChainStore auditChainStore;

    @Test
    void zeroLimitIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().listRecent(null, 0)
        );

        assertEquals("Audit limit must be positive", exception.getMessage());
        verify(repository, never()).findAllByOrderByOccurredAtDesc(any());
    }

    @Test
    void negativeLimitIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().listRecent("auth", -3)
        );

        assertEquals("Audit limit must be positive", exception.getMessage());
        verify(repository, never()).findByCategoryOrderByOccurredAtDesc(any(), any());
    }

    @Test
    void zeroExportLimitIsRejectedBeforeWriting() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service().streamCsv(null, 0, new ByteArrayOutputStream())
        );

        verify(repository, never()).findAllByOrderByOccurredAtDesc(any());
    }

    @Test
    void positiveLimitIsQueried() {
        when(repository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, 5))).thenReturn(List.of());

        assertEquals(List.of(), service().listRecent(null, 5));
    }

    private AuditEventService service() {
        return new AuditEventService(repository, objectMapper, siemWebhookDispatcher, auditChainStore);
    }
}
