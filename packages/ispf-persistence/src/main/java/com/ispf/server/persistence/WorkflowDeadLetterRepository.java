package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.WorkflowDeadLetterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowDeadLetterRepository extends JpaRepository<WorkflowDeadLetterEntity, String> {

    List<WorkflowDeadLetterEntity> findByWorkflowPathOrderByCreatedAtDesc(String workflowPath);

    List<WorkflowDeadLetterEntity> findByWorkflowPathAndResolvedAtIsNullOrderByCreatedAtDesc(String workflowPath);
}
