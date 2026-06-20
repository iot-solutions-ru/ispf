package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.ObjectVariableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ObjectVariableRepository extends JpaRepository<ObjectVariableEntity, Long> {

    List<ObjectVariableEntity> findByObjectPath(String objectPath);

    List<ObjectVariableEntity> findByHistoryEnabledTrue();

    long countByHistoryEnabledTrue();

    Optional<ObjectVariableEntity> findByObjectPathAndName(String objectPath, String name);

    void deleteByObjectPath(String objectPath);

    void deleteByObjectPathAndName(String objectPath, String name);

    void deleteByObjectPathStartingWith(String objectPathPrefix);
}
