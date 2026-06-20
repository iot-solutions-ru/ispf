package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.WorkflowInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstanceEntity, String> {

    Optional<WorkflowInstanceEntity> findFirstByWorkflowPathOrderByStartedAtDesc(String workflowPath);

    List<WorkflowInstanceEntity> findByStatusOrderByStartedAtDesc(String status);

    List<WorkflowInstanceEntity> findByWorkflowPathAndStatus(String workflowPath, String status);

    long countByStatus(String status);
}
