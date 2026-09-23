package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.WorkflowExecutionStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowExecutionStepRepository extends JpaRepository<WorkflowExecutionStepEntity, String> {

    List<WorkflowExecutionStepEntity> findByInstanceIdOrderBySeqAsc(String instanceId);

    List<WorkflowExecutionStepEntity> findByWorkflowPathOrderByStartedAtDesc(String workflowPath);
}
