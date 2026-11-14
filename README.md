# Candidate Management Service (crm_demo)

A production-grade RESTful backend application built with Java 21, Spring Boot 3, and Apache Maven for managing candidate records, executing dynamic multi-criteria searches, and performing high-throughput batch imports with concurrency.

---

## 1. Prerequisites

- Java Development Kit (JDK) 21 LTS
- Apache Maven 3.8+
- Git

Verify your local environment:
```bash
java -version
mvn -version
```

---

## 2. Quickstart & Running the Application

### Clone and Navigate
```bash
git clone <repository-url>
cd crm_demo
```

### Build the Application
```bash
mvn clean package
```

### Run the Application
```bash
mvn spring-boot:run
```

The application will start on port `8080`.

- Base URL: `http://localhost:8080`
- Swagger UI (OpenAPI Docs): `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON Spec: `http://localhost:8080/v3/api-docs`
- H2 Web Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:crm_demo_db`, Username: `sa`, Password: empty)

Sample seed records are loaded on application startup for immediate testing.

---

## 3. Running Automated Tests

Run the full suite of unit and integration tests:
```bash
mvn clean test
```

### Test Coverage Highlights
- Unit Tests: `CandidateServiceTest` verifies candidate business rules, duplicate validation, status transitions, and domain event triggering.
- Concurrency Tests: `CandidateBatchProcessorTest` tests multi-threaded batch chunk execution across worker threads with duplicate detection and error isolation.
- Controller Web Tests: `CandidateControllerTest` verifies REST endpoints, HTTP status codes, request validations, and global error handling using MockMvc.
- Persistence Tests: `CandidateRepositoryTest` validates dynamic criteria queries and database constraints using `@DataJpaTest`.

---

## 4. API Endpoints Reference

### 4.1 Create Candidate
- Method: `POST`
- Path: `/api/v1/candidates`
- Request:
```bash
curl -X POST http://localhost:8080/api/v1/candidates \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Robert",
    "lastName": "Davis",
    "email": "robert.davis@example.com",
    "phone": "+1-555-4433",
    "currentTitle": "Principal Systems Engineer",
    "yearsOfExperience": 10,
    "expectedSalary": 160000.00,
    "location": "San Francisco, CA",
    "skills": ["Java", "Distributed Systems", "Go"],
    "status": "APPLIED",
    "summary": "Experienced distributed systems and platform architect."
  }'
```
- Response: `201 Created`

### 4.2 View Candidate by ID
- Method: `GET`
- Path: `/api/v1/candidates/{id}`
```bash
curl -X GET http://localhost:8080/api/v1/candidates/1
```
- Response: `200 OK`

### 4.3 Search and Filter Candidates
- Method: `GET`
- Path: `/api/v1/candidates`
- Query Parameters:
  - `keyword`: Matches against first name, last name, email, current title, and summary (case-insensitive substring)
  - `skills`: Skill tag filter (e.g. `Java`, `Docker`)
  - `status`: `APPLIED`, `SCREENING`, `INTERVIEWED`, `OFFERED`, `HIRED`, `REJECTED`
  - `minExperience`: Minimum years of experience
  - `maxExperience`: Maximum years of experience
  - `location`: Location filter
  - `minSalary` / `maxSalary`: Expected salary bounds
  - `page`: Page number (default `0`)
  - `size`: Page size (default `10`)
  - `sort`: Sort property and direction (default `createdAt,desc`)

Example:
```bash
curl -X GET "http://localhost:8080/api/v1/candidates?keyword=Java&minExperience=4&status=SCREENING&page=0&size=10&sort=yearsOfExperience,desc"
```
- Response: `200 OK` (Spring Data `Page` response with content and pagination metadata)

### 4.4 Update Candidate
- Method: `PUT`
- Path: `/api/v1/candidates/{id}`
```bash
curl -X PUT http://localhost:8080/api/v1/candidates/1 \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-0101",
    "currentTitle": "Lead Java Architect",
    "yearsOfExperience": 8,
    "expectedSalary": 145000.00,
    "location": "New York, NY",
    "skills": ["Java", "Spring Boot", "Microservices", "Docker", "Kubernetes"],
    "status": "INTERVIEWED",
    "summary": "Updated candidate profile following preliminary technical screening."
  }'
```
- Response: `200 OK`

### 4.5 Delete Candidate
- Method: `DELETE`
- Path: `/api/v1/candidates/{id}`
```bash
curl -X DELETE http://localhost:8080/api/v1/candidates/1
```
- Response: `204 No Content`

### 4.6 Multi-Threaded Bulk Import (JSON)
- Method: `POST`
- Path: `/api/v1/candidates/batch`
```bash
curl -X POST http://localhost:8080/api/v1/candidates/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "firstName": "Sarah",
      "lastName": "Jenkins",
      "email": "sarah.jenkins@example.com",
      "phone": "+1-555-0001",
      "currentTitle": "Principal Cloud Architect",
      "yearsOfExperience": 25,
      "expectedSalary": 200000.00,
      "location": "Portland, OR",
      "skills": ["C", "Git", "Linux", "Java"],
      "status": "INTERVIEWED",
      "summary": "Cloud platform and infrastructure architecture specialist."
    },
    {
      "firstName": "James",
      "lastName": "Miller",
      "email": "david.miller@example.com",
      "phone": "+1-555-0002",
      "currentTitle": "Staff Software Engineer",
      "yearsOfExperience": 30,
      "expectedSalary": 220000.00,
      "location": "San Jose, CA",
      "skills": ["Java", "JVM", "Compilers"],
      "status": "OFFERED",
      "summary": "Experienced backend engineer focused on resilient microservice ecosystems."
    }
  ]'
```
- Response: `200 OK` with execution summary, timing, and error details if any.

### 4.7 Multi-Threaded Bulk Upload (CSV)
- Method: `POST`
- Path: `/api/v1/candidates/bulk-upload-csv`
```bash
curl -X POST http://localhost:8080/api/v1/candidates/bulk-upload-csv \
  -F "file=@candidates_sample.csv"
```
- Response: `200 OK`

---

## 5. Multi-Threading & Concurrency Architecture

The service leverages concurrency in three distinct layers:

1. Java 21 Project Loom (Virtual Threads)
   - Configured via `spring.threads.virtual.enabled: true`.
   - Every incoming HTTP request is dispatched onto a lightweight virtual thread, enabling high connection concurrency without saturating kernel thread limits.

2. Multi-Threaded Chunked Batch Processing (`CandidateBatchProcessor`)
   - Large imports are divided into balanced sub-batches (configurable chunk size).
   - Each chunk is processed concurrently by a dedicated `ThreadPoolTaskExecutor` using `CompletableFuture`.
   - Individual record failures (e.g., duplicate email, invalid parameters) are recorded without failing the remaining candidates in the chunk.

3. Asynchronous Event-Driven Audit Pipeline (`@Async`)
   - Candidate creation and status transitions publish domain events (`CandidateCreatedEvent`, `CandidateStatusChangedEvent`).
   - Listeners execute on a segregated `auditTaskExecutor` thread pool, persisting audit records without adding latency to the primary transaction.

---

## 6. Project Layout

```
crm_demo
├── pom.xml
├── README.md
├── DESIGN.md
├── AI_USAGE.md
├── candidates_sample.csv
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── crm_demo
│   │   │           ├── CandidateManagementApplication.java
│   │   │           ├── config
│   │   │           │   ├── AsyncConfig.java
│   │   │           │   ├── DataLoader.java
│   │   │           │   └── OpenApiConfig.java
│   │   │           ├── controller
│   │   │           │   └── CandidateController.java
│   │   │           ├── dto
│   │   │           │   ├── BulkImportResponse.java
│   │   │           │   ├── CandidateFilterCriteria.java
│   │   │           │   ├── CandidateRequest.java
│   │   │           │   ├── CandidateResponse.java
│   │   │           │   └── ErrorResponse.java
│   │   │           ├── entity
│   │   │           │   ├── AuditLog.java
│   │   │           │   ├── Candidate.java
│   │   │           │   └── CandidateStatus.java
│   │   │           ├── event
│   │   │           │   ├── CandidateCreatedEvent.java
│   │   │           │   ├── CandidateEventListener.java
│   │   │           │   └── CandidateStatusChangedEvent.java
│   │   │           ├── exception
│   │   │           │   ├── BadRequestException.java
│   │   │           │   ├── DuplicateResourceException.java
│   │   │           │   ├── GlobalExceptionHandler.java
│   │   │           │   └── ResourceNotFoundException.java
│   │   │           ├── repository
│   │   │           │   ├── AuditLogRepository.java
│   │   │           │   └── CandidateRepository.java
│   │   │           ├── service
│   │   │           │   ├── CandidateService.java
│   │   │           │   └── impl
│   │   │           │       ├── CandidateBatchProcessor.java
│   │   │           │       └── CandidateServiceImpl.java
│   │   │           └── specification
│   │   │               └── CandidateSpecification.java
│   │   └── resources
│   │       └── application.yml
│   └── test
│       └── java
│           └── com
│               └── crm_demo
│                   ├── controller
│                   │   └── CandidateControllerTest.java
│                   ├── repository
│                   │   └── CandidateRepositoryTest.java
│                   └── service
│                       ├── CandidateBatchProcessorTest.java
│                       └── CandidateServiceTest.java
```
