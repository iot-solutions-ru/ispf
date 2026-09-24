package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.WorkflowUserTaskEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkflowUserTaskRepository extends JpaRepository<WorkflowUserTaskEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM WorkflowUserTaskEntity t WHERE t.id = :id")
    Optional<WorkflowUserTaskEntity> findByIdForUpdate(@Param("id") String id);

    List<WorkflowUserTaskEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<WorkflowUserTaskEntity> findByStatusInOrderByCreatedAtDesc(List<String> statuses, Pageable pageable);

    List<WorkflowUserTaskEntity> findByStatusInAndOperatorAppIdOrderByCreatedAtDesc(
            List<String> statuses,
            String operatorAppId,
            Pageable pageable
    );

    Optional<WorkflowUserTaskEntity> findByInstanceIdAndStatus(String instanceId, String status);

    Optional<WorkflowUserTaskEntity> findByInstanceIdAndTaskNodeIdAndStatus(
            String instanceId,
            String taskNodeId,
            String status
    );

    List<WorkflowUserTaskEntity> findByInstanceId(String instanceId);
}
