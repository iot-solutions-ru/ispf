package com.ispf.server.persistence;

import com.ispf.core.object.ObjectType;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ObjectNodeRepository extends JpaRepository<ObjectNodeEntity, String> {

    Optional<ObjectNodeEntity> findByPath(String path);

    boolean existsByPath(String path);

    List<ObjectNodeEntity> findAllByOrderByPathAsc();

    long countByType(ObjectType type);
}
