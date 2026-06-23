package com.intelliflow.controller;

import com.intelliflow.model.AuditLog;
import com.intelliflow.model.Document;
import com.intelliflow.service.AuditService;
import com.intelliflow.service.DocumentService;
import com.intelliflow.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentService documentService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public DocumentController(DocumentService documentService,
                              AuditService auditService,
                              NotificationService notificationService) {
        this.documentService = documentService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @PostMapping("/documents/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            Document doc = documentService.uploadDocument(file);
            return ResponseEntity.ok(Map.of(
                "message", "Document uploaded successfully",
                "documentId", doc.getId(),
                "fileName", doc.getFileName(),
                "status", doc.getStatus()
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to upload: " + e.getMessage()));
        }
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<Document> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    @GetMapping("/documents")
    public ResponseEntity<List<Document>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @GetMapping("/documents/status/{status}")
    public ResponseEntity<List<Document>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(documentService.getDocumentsByStatus(status));
    }

    @GetMapping("/documents/{id}/audit")
    public ResponseEntity<List<AuditLog>> getAuditLog(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getLogsForDocument(id));
    }

    @PostMapping("/documents/{id}/approve")
    public ResponseEntity<Document> approveDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.approveDocument(id));
    }

    @PostMapping("/documents/{id}/reject")
    public ResponseEntity<Document> rejectDocument(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "Rejected by reviewer");
        return ResponseEntity.ok(documentService.rejectDocument(id, reason));
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationService.Notification>> getNotifications() {
        return ResponseEntity.ok(notificationService.getAllNotifications());
    }

    @GetMapping("/notifications/unread")
    public ResponseEntity<List<NotificationService.Notification>> getUnread() {
        return ResponseEntity.ok(notificationService.getUnread());
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<?> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        List<Document> all = documentService.getAllDocuments();
        long completed = all.stream().filter(d -> "COMPLETED".equals(d.getStatus())).count();
        long needsReview = all.stream().filter(d -> "NEEDS_REVIEW".equals(d.getStatus())).count();
        long failed = all.stream().filter(d -> "FAILED".equals(d.getStatus())).count();
        long processing = all.stream().filter(d -> "PROCESSING".equals(d.getStatus())
                || "UPLOADED".equals(d.getStatus()) || "EXTRACTED".equals(d.getStatus())).count();

        double avgConfidence = all.stream()
                .filter(d -> d.getConfidenceScore() != null)
                .mapToDouble(Document::getConfidenceScore)
                .average()
                .orElse(0.0);

        return ResponseEntity.ok(Map.of(
            "total_documents", all.size(),
            "completed", completed,
            "needs_review", needsReview,
            "failed", failed,
            "processing", processing,
            "average_confidence", Math.round(avgConfidence * 100.0) / 100.0,
            "unread_notifications", notificationService.getUnread().size()
        ));
    }
}