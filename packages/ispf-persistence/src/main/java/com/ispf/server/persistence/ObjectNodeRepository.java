package com.ispf.server.persistence;

import com.ispf.core.object.ObjectType;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ObjectNodeRepository extends JpaRepository<ObjectNodeEntity, String> {

    Optional<ObjectNodeEntity> findByPath(String path);

    boolean existsByPath(String path);

    List<ObjectNodeEntity> findAllByOrderByPathAsc();

    /**
     * Path and descendants, deepest paths first (safe delete order).
     */
    @Query("""
            SELECT n FROM ObjectNodeEntity n
            WHERE n.path = :path OR n.path LIKE CONCAT(:path, '.%')
            ORDER BY LENGTH(n.path) DESC
            """)
    List<ObjectNodeEntity> findByPathPrefixOrderByPathLengthDesc(@Param("path") String path);

    long countByType(ObjectType type);
}
