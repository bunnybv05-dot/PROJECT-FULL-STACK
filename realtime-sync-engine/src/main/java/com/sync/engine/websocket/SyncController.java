package com.sync.engine.websocket;

import com.sync.engine.dto.SyncOperation;
import com.sync.engine.entity.Document;
import com.sync.engine.entity.DocumentOperation;
import com.sync.engine.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
public class SyncController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/document.edit")
    @SendTo("/topic/document.updates")
    public SyncOperation handleDocumentEdit(@Payload SyncOperation operation) {
        documentService.applyOperation(operation);
        operation.setTimestamp(System.currentTimeMillis());
        return operation;
    }

    @MessageMapping("/document.get")
    public void getDocument(@Payload Map<String, Long> payload) {
        Long documentId = payload.get("documentId");
        Document document = documentService.getDocument(documentId);
        messagingTemplate.convertAndSend("/queue/document." + documentId, document);
    }

    @MessageMapping("/document.sync")
    public void syncDocument(@Payload Map<String, Object> payload) {
        Long documentId = ((Number) payload.get("documentId")).longValue();
        Long lastVersion = ((Number) payload.get("lastVersion")).longValue();

        List<DocumentOperation> operations = documentService.getOperationsAfterVersion(documentId, lastVersion);

        messagingTemplate.convertAndSend("/queue/document.sync." + documentId, operations);
    }
}
