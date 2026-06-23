package com.intelliflow.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkflowAgent {

    private final ChatLanguageModel chatModel;

    public WorkflowAgent(@Value("${openai.api-key}") String apiKey,
                         @Value("${openai.model}") String model) {
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.0)
                .build();
    }

    public WorkflowPlan analyzeAndPlan(String rawText) {
        String prompt = """
                You are a document classification and workflow planning agent.
                
                Analyze the following document text and determine:
                1. The document type (invoice, resume, receipt, contract, report, letter, or unknown)
                2. The processing steps needed
                3. What data to extract
                4. What validation rules to apply
                
                Return ONLY valid JSON with no additional text:
                {
                    "document_type": "",
                    "confidence": 0.0,
                    "processing_steps": ["step1", "step2"],
                    "extraction_fields": ["field1", "field2"],
                    "validation_rules": ["rule1", "rule2"],
                    "priority": "low|medium|high",
                    "reasoning": "Brief explanation of classification"
                }
                
                DOCUMENT TEXT:
                \"\"\"
                %s
                \"\"\"
                """.formatted(rawText.length() > 3000 ? rawText.substring(0, 3000) : rawText);

        String response = chatModel.generate(prompt);
        return parseWorkflowPlan(response);
    }

    private WorkflowPlan parseWorkflowPlan(String json) {
        WorkflowPlan plan = new WorkflowPlan();
        plan.setRawPlan(json);
        plan.setDocumentType(extractJsonString(json, "document_type"));
        plan.setConfidence(extractJsonDouble(json, "confidence"));
        plan.setPriority(extractJsonString(json, "priority"));
        plan.setReasoning(extractJsonString(json, "reasoning"));
        return plan;
    }

    private String extractJsonString(String json, String field) {
        try {
            String marker = "\"" + field + "\"";
            int idx = json.indexOf(marker);
            if (idx == -1) return "unknown";
            int start = json.indexOf("\"", idx + marker.length() + 1);
            int end = json.indexOf("\"", start + 1);
            return json.substring(start + 1, end);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Double extractJsonDouble(String json, String field) {
        try {
            String marker = "\"" + field + "\"";
            int idx = json.indexOf(marker);
            if (idx == -1) return 0.0;
            int colonIdx = json.indexOf(":", idx);
            int endIdx = json.indexOf(",", colonIdx);
            if (endIdx == -1) endIdx = json.indexOf("}", colonIdx);
            return Double.parseDouble(json.substring(colonIdx + 1, endIdx).trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static class WorkflowPlan {
        private String rawPlan;
        private String documentType;
        private Double confidence;
        private String priority;
        private String reasoning;

        public String getRawPlan() { return rawPlan; }
        public void setRawPlan(String rawPlan) { this.rawPlan = rawPlan; }
        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    }
}