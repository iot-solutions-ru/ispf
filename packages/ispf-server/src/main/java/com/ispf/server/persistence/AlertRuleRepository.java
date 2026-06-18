package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.AlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, String> {

    List<AlertRuleEntity> findByObjectPathAndWatchVariableAndEnabledTrue(
            String objectPath,
            String watchVariable
    );

    long count();
}
