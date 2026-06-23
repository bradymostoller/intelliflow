package com.intelliflow.service;

import com.intelliflow.model.AuditLog;
import com.intelliflow.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(Long documentId, String action, String previousStatus,
                    String newStatus, String details, String performedBy) {
        AuditLog log = new AuditLog();
        log.setDocumentId(documentId);
        log.setAction(action);
        log.setPreviousStatus(previousStatus);
        log.setNewStatus(newStatus);
        log.setDetails(details);
        log.setPerformedBy(performedBy);
        auditLogRepository.save(log);
    }

    public List<AuditLog> getLogsForDocument(Long documentId) {
        return auditLogRepository.findByDocumentIdOrderByTimestampAsc(documentId);
    }
}