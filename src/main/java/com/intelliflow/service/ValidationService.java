package com.intelliflow.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ValidationService {

    public ValidationResult validate(String extractedDataJson, Double confidenceScore) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean needsReview = false;

        // Rule 1: Confidence score check
        if (confidenceScore == null || confidenceScore < 0.7) {
            errors.add("Low confidence score: " + confidenceScore);
            needsReview = true;
        } else if (confidenceScore < 0.85) {
            warnings.add("Moderate confidence score: " + confidenceScore);
        }

        // Rule 2: Check if extracted data is present
        if (extractedDataJson == null || extractedDataJson.trim().isEmpty()) {
            errors.add("No structured data was extracted");
            needsReview = true;
            return new ValidationResult(errors, warnings, needsReview);
        }

        // Rule 3: Invoice-specific validations
        if (extractedDataJson.contains("\"document_type\": \"invoice\"")
                || extractedDataJson.contains("\"document_type\":\"invoice\"")) {
            validateInvoice(extractedDataJson, errors, warnings);
            if (!errors.isEmpty()) {
                needsReview = true;
            }
        }

        // Rule 4: Resume-specific validations
        if (extractedDataJson.contains("\"document_type\": \"resume\"")
                || extractedDataJson.contains("\"document_type\":\"resume\"")) {
            validateResume(extractedDataJson, errors, warnings);
        }

        return new ValidationResult(errors, warnings, needsReview);
    }

    private void validateInvoice(String json, List<String> errors, List<String> warnings) {
        // Check for required invoice fields
        if (!json.contains("\"invoice_number\"") || containsEmpty(json, "invoice_number")) {
            errors.add("Missing invoice number");
        }
        if (!json.contains("\"vendor_name\"") || containsEmpty(json, "vendor_name")) {
            errors.add("Missing vendor name");
        }
        if (!json.contains("\"total_amount\"")) {
            errors.add("Missing total amount");
        }
        if (!json.contains("\"invoice_date\"") || containsEmpty(json, "invoice_date")) {
            warnings.add("Missing invoice date");
        }
        if (!json.contains("\"line_items\"") || json.contains("\"line_items\": []")) {
            warnings.add("No line items found");
        }
    }

    private void validateResume(String json, List<String> errors, List<String> warnings) {
        if (!json.contains("\"name\"") || containsEmpty(json, "name")) {
            errors.add("Missing candidate name");
        }
        if (!json.contains("\"email\"") || containsEmpty(json, "email")) {
            warnings.add("Missing email address");
        }
        if (!json.contains("\"work_experience\"") || json.contains("\"work_experience\": []")) {
            warnings.add("No work experience found");
        }
    }

    private boolean containsEmpty(String json, String field) {
        return json.contains("\"" + field + "\": \"\"") || json.contains("\"" + field + "\":\"\"");
    }

    public static class ValidationResult {
        private final List<String> errors;
        private final List<String> warnings;
        private final boolean needsReview;

        public ValidationResult(List<String> errors, List<String> warnings, boolean needsReview) {
            this.errors = errors;
            this.warnings = warnings;
            this.needsReview = needsReview;
        }

        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public boolean isNeedsReview() { return needsReview; }

        public boolean passed() { return errors.isEmpty(); }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"errors\": [");
            for (int i = 0; i < errors.size(); i++) {
                sb.append("\"").append(errors.get(i)).append("\"");
                if (i < errors.size() - 1) sb.append(", ");
            }
            sb.append("], \"warnings\": [");
            for (int i = 0; i < warnings.size(); i++) {
                sb.append("\"").append(warnings.get(i)).append("\"");
                if (i < warnings.size() - 1) sb.append(", ");
            }
            sb.append("], \"needs_review\": ").append(needsReview);
            sb.append("}");
            return sb.toString();
        }
    }
}