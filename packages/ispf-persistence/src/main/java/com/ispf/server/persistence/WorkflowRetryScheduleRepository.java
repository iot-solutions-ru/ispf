package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.WorkflowRetryScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WorkflowRetryScheduleRepository extends JpaRepository<WorkflowRetryScheduleEntity, String> {

    @Query("""
            SELECT r FROM WorkflowRetryScheduleEntity r
            WHERE r.status = 'PENDING' AND r.dueAt <= :now
            ORDER BY r.dueAt ASC
            """)
    List<WorkflowRetryScheduleEntity> findDue(@Param("now") Instant now);

    List<WorkflowRetryScheduleEntity> findByWorkflowPathOrderByCreatedAtDesc(String workflowPath);
}
