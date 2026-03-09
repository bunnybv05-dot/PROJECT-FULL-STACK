package com.sync.engine.service;

import com.sync.engine.dto.SyncOperation;
import com.sync.engine.entity.Document;
import com.sync.engine.entity.DocumentOperation;
import com.sync.engine.repository.DocumentOperationRepository;
import com.sync.engine.repository.DocumentRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentOperationRepository operationRepository;

    @Transactional
    public Document createDocument(String title, Long userId) {
        Document doc = new Document();
        doc.setTitle(title);
        doc.setContent("");
        doc.setCreatedBy(userId);
        doc.setVersion(0L);
        return documentRepository.save(doc);
    }

    public Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }

    @Transactional
    public void applyOperation(SyncOperation operation) {
        Long documentId = operation.getDocumentId();
        Document document = getDocument(documentId);

        DocumentOperation docOp = new DocumentOperation();
        docOp.setDocumentId(documentId);
        docOp.setOperationType(operation.getType());
        docOp.setPosition(operation.getPosition());
        docOp.setTextContent(operation.getData() != null ? operation.getData().toString() : null);

        try {
            docOp.setUserId(operation.getUserId() != null ? Long.parseLong(operation.getUserId()) : null);
        } catch (NumberFormatException e) {
            docOp.setUserId(null);
        }

        docOp.setVersion(document.getVersion() + 1);
        operationRepository.save(docOp);

        if ("UPDATE".equals(operation.getType()) && operation.getData() != null) {
            document.setContent(operation.getData().toString());
        }
        document.setVersion(document.getVersion() + 1);
        documentRepository.save(document);
    }

    public List<DocumentOperation> getOperationsAfterVersion(Long documentId, Long version) {
        return operationRepository.findByDocumentIdAndVersionGreaterThan(documentId, version);
    }
}
