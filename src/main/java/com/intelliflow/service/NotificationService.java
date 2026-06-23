package com.intelliflow.service;

import com.intelliflow.model.AuditLog;
import com.intelliflow.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {

    private final AuditLogRepository auditLogRepository;
    private final List<Notification> notifications = new ArrayList<>();

    public NotificationService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void notify(Long documentId, String type, String message) {
        Notification notification = new Notification(documentId, type, message);
        notifications.add(notification);

        // Log notification as audit entry too
        System.out.println("[NOTIFICATION] [" + type + "] Document #" + documentId + ": " + message);
    }

    public void notifyFailure(Long documentId, String error) {
        notify(documentId, "FAILURE", "Processing failed: " + error);
    }

    public void notifyNeedsReview(Long documentId, String reason) {
        notify(documentId, "REVIEW_NEEDED", "Document flagged for review: " + reason);
    }

    public void notifyCompleted(Long documentId) {
        notify(documentId, "COMPLETED", "Document processing completed successfully");
    }

    public List<Notification> getAllNotifications() {
        return new ArrayList<>(notifications);
    }

    public List<Notification> getUnread() {
        return notifications.stream()
                .filter(n -> !n.isRead())
                .toList();
    }

    public void markAllRead() {
        notifications.forEach(n -> n.setRead(true));
    }

    public static class Notification {
        private final Long documentId;
        private final String type;
        private final String message;
        private final long timestamp;
        private boolean read;

        public Notification(Long documentId, String type, String message) {
            this.documentId = documentId;
            this.type = type;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.read = false;
        }

        public Long getDocumentId() { return documentId; }
        public String getType() { return type; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
        public boolean isRead() { return read; }
        public void setRead(boolean read) { this.read = read; }
    }
}