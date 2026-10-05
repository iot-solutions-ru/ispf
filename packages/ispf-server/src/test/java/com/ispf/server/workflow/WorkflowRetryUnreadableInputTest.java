package com.ispf.server.workflow;

import com.ispf.server.persistence.WorkflowRetryScheduleRepository;
import com.ispf.server.persistence.entity.WorkflowRetryScheduleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowRetryUnreadableInputTest {

    @Autowired
    private WorkflowRetryService retryService;

    @Autowired
    private WorkflowRetryScheduleRepository repository;

    @Autowired
    private WorkflowRetryScheduler retryScheduler;

    @Test
    void unreadableStoredInputFailsTheRetry() {
        WorkflowRetryScheduleEntity saved = retryService.schedule(
                "root.platform.workflows.retry-unreadable-input",
                "inst-bad",
                1,
                Instant.now().minusSeconds(5),
                Map.of("marker", "seen"),
                "seed"
        );
        saved.setInputJson("{not-json");
        repository.saveAndFlush(saved);

        retryScheduler.runDueRetries();

        WorkflowRetryScheduleEntity after = repository.findById(saved.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(WorkflowRetryService.STATUS_FAILED);
        assertThat(after.getLastError()).contains("Workflow retry input JSON is not readable");
    }
}
