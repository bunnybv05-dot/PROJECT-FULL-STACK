package com.sync.engine.repository;

import com.sync.engine.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByCreatedBy(Long userId);
}
