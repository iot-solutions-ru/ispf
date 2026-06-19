package com.ispf.server.workflow;

import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.entity.WorkflowInstanceEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowInstanceCancelService {

    private final WorkflowInstanceRepository instanceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationSchemaSession schemaSession;
    private final String cancelJournalTable;

    public WorkflowInstanceCancelService(
            WorkflowInstanceRepository instanceRepository,
            JdbcTemplate jdbcTemplate,
            ApplicationSchemaSession schemaSession,
            PlatformSqlCatalog platformSqlCatalog
    ) {
        this.instanceRepository = instanceRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.schemaSession = schemaSession;
        this.cancelJournalTable = platformSqlCatalog.table("workflow_cancel_journal");
    }

    @Transactional
    public Map<String, Object> cancel(String instanceId, String reason, String detailJson, String cancelledBy) {
        WorkflowInstanceEntity entity = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow instance not found: " + instanceId));

        if (InstanceStatus.FAILED.name().equals(entity.getStatus())
                || InstanceStatus.COMPLETED.name().equals(entity.getStatus())) {
            return Map.of(
                    "instanceId", instanceId,
                    "status", entity.getStatus(),
                    "cancelled", false,
                    "message", "Already final"
            );
        }

        entity.setStatus(InstanceStatus.FAILED.name());
        entity.setUpdatedAt(Instant.now());
        instanceRepository.save(entity);

        jdbcTemplate.update("""
                INSERT INTO %s (
                    id, instance_id, workflow_path, reason, detail_json, cancelled_by, cancelled_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.formatted(cancelJournalTable),
                UUID.randomUUID(),
                instanceId,
                entity.getWorkflowPath(),
                reason,
                detailJson,
                cancelledBy,
                Timestamp.from(Instant.now())
        );

        return Map.of(
                "instanceId", instanceId,
                "status", InstanceStatus.FAILED.name(),
                "cancelled", true,
                "reason", reason
        );
    }

    @Transactional
    public int cancelWaitingByWorkflowPath(String workflowPath, String reason, String detailJson, String cancelledBy) {
        return cancelByWorkflowPath(workflowPath, List.of(InstanceStatus.WAITING.name()), reason, detailJson, cancelledBy);
    }

    @Transactional
    public int cancelByWorkflowPath(
            String workflowPath,
            List<String> statusIn,
            String reason,
            String detailJson,
            String cancelledBy
    ) {
        int[] count = new int[1];
        schemaSession.runWithPlatformCatalog(() ->
                count[0] = cancelByWorkflowPathOnPlatformCatalog(workflowPath, statusIn, reason, detailJson, cancelledBy)
        );
        return count[0];
    }

    private int cancelByWorkflowPathOnPlatformCatalog(
            String workflowPath,
            List<String> statusIn,
            String reason,
            String detailJson,
            String cancelledBy
    ) {
        List<String> statuses = statusIn == null || statusIn.isEmpty()
                ? List.of(InstanceStatus.WAITING.name())
                : statusIn;
        int count = 0;
        for (String status : statuses) {
            List<WorkflowInstanceEntity> instances = instanceRepository.findByWorkflowPathAndStatus(
                    workflowPath,
                    status
            );
            for (WorkflowInstanceEntity entity : instances) {
                cancel(entity.getId(), reason, detailJson, cancelledBy);
                count++;
            }
        }
        return count;
    }
}
