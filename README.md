IntelliFlow

A document processing pipeline that takes uploaded documents (invoices, statements, and similar), pulls the important fields out of them with OCR and an LLM, checks the results against some basic rules, and stores everything in Postgres. If the extraction comes back with low confidence or fails validation, the document gets flagged for someone to review instead of being processed automatically.

I built this to learn how to wire an LLM into a real backend workflow rather than just calling an API in a script — handling the messy parts like file upload, OCR, retries, validation, and keeping an audit trail.

How it works

The flow for a single document:


A document comes in through the REST endpoint and gets saved.
It runs through OCR to get raw text.
The text goes to the LLM, which returns structured fields (vendor, amount, dates, etc.) as JSON.
Those fields get validated against business rules.
Results are written to Postgres, and an audit log entry is recorded.
If confidence is low or validation fails, the document is marked for manual review.


Tech stack


Java / Spring Boot
PostgreSQL (via Spring Data JPA)
OpenAI API for field extraction
OCR for reading scanned/PDF documents
Maven


Running it locally

You'll need Java 17+, Maven, and a running Postgres instance.

First, create a database:

sqlCREATE DATABASE intelliflow;

The app reads secrets from environment variables, so set those before starting:

bashexport OPENAI_API_KEY="your-key-here"
export DB_PASSWORD="your-postgres-password"

Then run:

bash./mvnw spring-boot:run

The app starts on http://localhost:8080 by default.

Config

Most settings live in src/main/resources/application.yaml. The database URL, username, upload directory, and OpenAI model are configured there. Anything sensitive (API key, DB password) is pulled from environment variables and is not committed to the repo.

VariableWhat it's forOPENAI_API_KEYYour OpenAI API keyDB_PASSWORDPassword for the Postgres userDB_USERNAMEPostgres user (defaults to postgres)

Project layout

src/main/java/com/intelliflow/
  agent/        - orchestration logic for the processing workflow
  controller/   - REST endpoints
  service/      - OCR, LLM extraction, validation, notifications, audit
  model/        - entities (Document, AuditLog, status enums)
  repository/   - data access
frontend/       - simple dashboard
