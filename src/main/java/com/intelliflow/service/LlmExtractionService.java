package com.intelliflow.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LlmExtractionService {

    private final ChatLanguageModel chatModel;

    public LlmExtractionService(@Value("${openai.api-key}") String apiKey,
                                 @Value("${openai.model}") String model) {
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.0)
                .build();
    }

    public String extractStructuredData(String rawText, String documentType) {
        String prompt = buildExtractionPrompt(rawText, documentType);
        return chatModel.generate(prompt);
    }

    private String buildExtractionPrompt(String rawText, String documentType) {
        return """
                You are a document data extraction agent. Analyze the following document text
                and extract all structured data from it.

                Return ONLY valid JSON with no additional text or markdown formatting.

                For invoices, extract:
                {
                    "document_type": "invoice",
                    "vendor_name": "",
                    "vendor_address": "",
                    "invoice_number": "",
                    "invoice_date": "",
                    "due_date": "",
                    "bill_to": "",
                    "line_items": [
                        {
                            "description": "",
                            "quantity": 0,
                            "unit_price": 0.00,
                            "total": 0.00
                        }
                    ],
                    "subtotal": 0.00,
                    "tax": 0.00,
                    "total_amount": 0.00,
                    "payment_terms": "",
                    "notes": ""
                }

                For resumes, extract:
                {
                    "document_type": "resume",
                    "name": "",
                    "email": "",
                    "phone": "",
                    "linkedin": "",
                    "github": "",
                    "education": [
                        {
                            "school": "",
                            "degree": "",
                            "gpa": "",
                            "graduation_date": "",
                            "coursework": []
                        }
                    ],
                    "skills": {
                        "languages": [],
                        "frameworks": [],
                        "cloud_and_databases": []
                    },
                    "work_experience": [
                        {
                            "title": "",
                            "company": "",
                            "location": "",
                            "dates": "",
                            "responsibilities": []
                        }
                    ],
                    "projects": [
                        {
                            "name": "",
                            "technologies": [],
                            "description": []
                        }
                    ]
                }

                For any other document, extract all key-value pairs and structured data
                you can identify, using a reasonable JSON schema.

                Also include a "confidence_score" field (0.0 to 1.0) indicating how confident
                you are in the accuracy of the extraction.

                DOCUMENT TEXT:
                \"\"\"
                %s
                \"\"\"
                """.formatted(rawText);
    }
}