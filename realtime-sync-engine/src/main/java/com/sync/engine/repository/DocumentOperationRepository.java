package com.sync.engine.repository;

import com.sync.engine.entity.DocumentOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentOperationRepository extends JpaRepository<DocumentOperation, Long> {
    List<DocumentOperation> findByDocumentIdOrderByVersionAsc(Long documentId);

    List<DocumentOperation> findByDocumentIdAndVersionGreaterThan(Long documentId, Long version);
}
