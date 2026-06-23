package com.intelliflow.service;

import com.intelliflow.agent.WorkflowAgent;
import com.intelliflow.model.Document;
import com.intelliflow.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final OcrService ocrService;
    private final LlmExtractionService llmExtractionService;
    private final ValidationService validationService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final WorkflowAgent workflowAgent;
    private final Path uploadPath;

    public DocumentService(DocumentRepository documentRepository,
                           OcrService ocrService,
                           LlmExtractionService llmExtractionService,
                           ValidationService validationService,
                           AuditService auditService,
                           NotificationService notificationService,
                           WorkflowAgent workflowAgent,
                           @Value("${app.upload-dir}") String uploadDir) {
        this.documentRepository = documentRepository;
        this.ocrService = ocrService;
        this.llmExtractionService = llmExtractionService;
        this.validationService = validationService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.workflowAgent = workflowAgent;
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    public Document uploadDocument(MultipartFile file) throws IOException {
        String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path targetPath = this.uploadPath.resolve(uniqueFileName);

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        Document doc = new Document();
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(file.getContentType());
        doc.setFilePath(targetPath.toString());
        doc.setFileSize(file.getSize());

        doc = documentRepository.save(doc);

        auditService.log(doc.getId(), "DOCUMENT_UPLOADED", null, "UPLOADED",
                "File: " + file.getOriginalFilename() + ", Size: " + file.getSize() + " bytes", "SYSTEM");

        processDocument(doc);

        return doc;
    }

    public void processDocument(Document doc) {
        try {
            // Step 1: OCR / Text Extraction
            String previousStatus = doc.getStatus();
            doc.setStatus("PROCESSING");
            documentRepository.save(doc);
            auditService.log(doc.getId(), "OCR_STARTED", previousStatus, "PROCESSING",
                    "Starting text extraction", "SYSTEM");

            File file = new File(doc.getFilePath());
            String extractedText = ocrService.extractText(file, doc.getFileType());

            doc.setRawText(extractedText);
            doc.setStatus("EXTRACTED");
            documentRepository.save(doc);
            auditService.log(doc.getId(), "OCR_COMPLETED", "PROCESSING", "EXTRACTED",
                    "Extracted " + extractedText.length() + " characters", "SYSTEM");

            // Step 2: Agentic Workflow Planning
            auditService.log(doc.getId(), "AGENT_PLANNING", "EXTRACTED", "EXTRACTED",
                    "AI agent analyzing document and planning workflow", "AGENT");

            WorkflowAgent.WorkflowPlan plan = workflowAgent.analyzeAndPlan(extractedText);

            auditService.log(doc.getId(), "AGENT_PLAN_CREATED", "EXTRACTED", "EXTRACTED",
                    "Type: " + plan.getDocumentType() + ", Priority: " + plan.getPriority()
                            + ", Reasoning: " + plan.getReasoning(), "AGENT");

            // Step 3: LLM Structured Data Extraction
            auditService.log(doc.getId(), "LLM_EXTRACTION_STARTED", "EXTRACTED", "EXTRACTED",
                    "Sending to OpenAI for structured extraction", "SYSTEM");

            String structuredData = llmExtractionService.extractStructuredData(
                    extractedText, doc.getFileType());

            doc.setExtractedData(structuredData);
            Double confidence = parseConfidenceScore(structuredData);
            doc.setConfidenceScore(confidence);
            documentRepository.save(doc);

            auditService.log(doc.getId(), "LLM_EXTRACTION_COMPLETED", "EXTRACTED", "EXTRACTED",
                    "Confidence score: " + confidence, "SYSTEM");

            // Step 4: Business Rule Validation
            ValidationService.ValidationResult result = validationService.validate(
                    structuredData, confidence);

            if (result.isNeedsReview()) {
                doc.setStatus("NEEDS_REVIEW");
                doc.setValidationErrors(result.toJson());
                doc.setProcessedAt(LocalDateTime.now());
                documentRepository.save(doc);
                auditService.log(doc.getId(), "FLAGGED_FOR_REVIEW", "EXTRACTED", "NEEDS_REVIEW",
                        "Validation issues: " + result.toJson(), "SYSTEM");
                notificationService.notifyNeedsReview(doc.getId(), result.toJson());
            } else if (!result.passed()) {
                doc.setStatus("VALIDATED");
                doc.setValidationErrors(result.toJson());
                doc.setProcessedAt(LocalDateTime.now());
                documentRepository.save(doc);
                auditService.log(doc.getId(), "VALIDATED_WITH_WARNINGS", "EXTRACTED", "VALIDATED",
                        "Warnings: " + result.toJson(), "SYSTEM");
                notificationService.notifyCompleted(doc.getId());
            } else {
                doc.setStatus("COMPLETED");
                doc.setProcessedAt(LocalDateTime.now());
                documentRepository.save(doc);
                auditService.log(doc.getId(), "VALIDATION_PASSED", "EXTRACTED", "COMPLETED",
                        "All business rules passed", "SYSTEM");
                notificationService.notifyCompleted(doc.getId());
            }

        } catch (Exception e) {
            doc.setStatus("FAILED");
            doc.setValidationErrors("Processing failed: " + e.getMessage());
            doc.setProcessedAt(LocalDateTime.now());
            documentRepository.save(doc);
            auditService.log(doc.getId(), "PROCESSING_FAILED", doc.getStatus(), "FAILED",
                    "Error: " + e.getMessage(), "SYSTEM");
            notificationService.notifyFailure(doc.getId(), e.getMessage());
        }
    }

    public Document approveDocument(Long id) {
        Document doc = getDocument(id);
        if (!"NEEDS_REVIEW".equals(doc.getStatus())) {
            throw new RuntimeException("Document is not pending review");
        }
        String previousStatus = doc.getStatus();
        doc.setStatus("COMPLETED");
        doc.setProcessedAt(LocalDateTime.now());
        documentRepository.save(doc);
        auditService.log(doc.getId(), "HUMAN_APPROVED", previousStatus, "COMPLETED",
                "Document manually approved", "HUMAN_REVIEWER");
        return doc;
    }

    public Document rejectDocument(Long id, String reason) {
        Document doc = getDocument(id);
        if (!"NEEDS_REVIEW".equals(doc.getStatus())) {
            throw new RuntimeException("Document is not pending review");
        }
        String previousStatus = doc.getStatus();
        doc.setStatus("FAILED");
        doc.setValidationErrors(reason);
        doc.setProcessedAt(LocalDateTime.now());
        documentRepository.save(doc);
        auditService.log(doc.getId(), "HUMAN_REJECTED", previousStatus, "FAILED",
                "Reason: " + reason, "HUMAN_REVIEWER");
        return doc;
    }

    private Double parseConfidenceScore(String json) {
        try {
            String marker = "\"confidence_score\"";
            int idx = json.indexOf(marker);
            if (idx == -1) return null;
            int colonIdx = json.indexOf(":", idx);
            int endIdx = json.indexOf(",", colonIdx);
            if (endIdx == -1) endIdx = json.indexOf("}", colonIdx);
            String value = json.substring(colonIdx + 1, endIdx).trim();
            return Double.parseDouble(value);
        } catch (Exception e) {
            return null;
        }
    }

    public Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAllByOrderByUploadedAtDesc();
    }

    public List<Document> getDocumentsByStatus(String status) {
        return documentRepository.findByStatus(status);
    }
}