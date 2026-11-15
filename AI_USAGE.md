# AI-Assisted Development Document

## 1. Overview
In accordance with the assignment guidelines, AI-assisted tools were utilized to accelerate development, improve test coverage, and explore design alternatives. This document transparently outlines where AI was applied, how outputs were validated, manual modifications made, and where AI suggestions were deliberately rejected.

---

## 2. Where AI Was Used

1. **Initial Project Scaffolding**:
   - Generating Maven configuration (`pom.xml`) with modern Java 21 dependencies (Spring Boot 3.3.5, Springdoc OpenAPI, Commons CSV, JaCoCo).
2. **Boilerplate & Record Drafting**:
   - Rapidly creating Java 21 Record definitions for request, response, and filter criteria DTOs with Jakarta Bean Validation annotations.
3. **JPA Specification Template**:
   - Generating the initial structure of dynamic predicate construction using JPA Criteria API (`CandidateSpecification`).
4. **Test Fixtures & MockMvc Scaffolding**:
   - Generating parameterized test cases and mock data fixtures for unit tests and controller integration tests.

---

## 3. How AI Was Used

AI was used as an interactive pair programming partner and technical accelerator:
- **Domain Exploration**: Prompted for common ATS candidate entity schemas, indexing best practices, and status lifecycle flows.
- **Test Case Generation**: Prompted to suggest edge cases for candidate search (e.g. boundary conditions for experience ranges, null skills, and duplicate emails).
- **CSV Parsing Logic**: Referenced the Apache Commons CSV 1.11 builder API syntax for streaming record parsing.

---

## 4. How AI Output Was Validated

Every piece of AI-generated code underwent strict technical validation:
1. **Automated Test Execution**:
   - Executed `mvn clean test` across all layers (Service, Controller, Batch Processor, Repository, Event Listener, Exception Handler).
   - Ensured all **66 automated tests** passed deterministically, achieving **100.0% line coverage** (616 / 616 lines) verified by JaCoCo.
2. **Static Analysis & SonarQube Compliance**:
   - Inspected code for SonarQube rules: verified all exceptions and JPA entities declare `serialVersionUID`, eliminated generic `catch (Exception)`, eliminated unused imports, and ensured parameterized SLF4J logging.
3. **Thread Safety & Concurrency Audit**:
   - Audited `CandidateBatchProcessor` and `AsyncConfig` to verify thread-safe state handling and bounded execution queues.
4. **Functional Verification**:
   - Tested API endpoints via Swagger UI and cURL to verify response codes, validation boundaries, and error formats.

---

## 5. Modifications Made to AI-Generated Output

Several adjustments were made to refine the AI's initial suggestions:
1. **Thread Pool Configuration**:
   - AI initially suggested using `Executors.newCachedThreadPool()`. This was replaced with a bounded `ThreadPoolTaskExecutor` featuring defined core/max pool sizes, queue capacity, and a `CallerRunsPolicy` to protect system memory under heavy load.
2. **Join Deduplication in Specifications**:
   - The initial AI-generated Criteria query joined the skills collection without `query.distinct(true)`, which would have produced duplicate candidate rows for multiple skill matches. This was corrected.
3. **Domain Constraint Tightening**:
   - Added realistic upper bounds to years of experience (`@Min(0)`, `@Max(80)`), required non-blank titles and locations, and enforced a minimum of 3 skills per candidate.
4. **Jackson Deserialization Diagnostics**:
   - Standard Spring Boot returns a generic HTTP 400 with a null field errors map when Jackson parsing fails. Enhanced `GlobalExceptionHandler` to inspect `InvalidFormatException` and extract the offending field name, rejected value, and acceptable enum constants into `fieldErrors`.
5. **Entity Serialization**:
   - Implemented `java.io.Serializable` with explicit `serialVersionUID` across JPA entities for cache and session replication compatibility.
6. **DTO Immutability**:
   - Rather than using mutable POJOs or Lombok annotations on DTOs, native Java 21 Records were selected for built-in immutability and clean syntax.

---

## 6. Where AI Recommendations Were Rejected and Why

1. **Introducing Elasticsearch and Redis in Initial Codebase**:
   - AI recommended immediately integrating Elasticsearch and Redis for full-text search and caching.
   - Decision: Rejected for the practical implementation. For an assignment submission, requiring reviewers to install external Docker containers (Elasticsearch, Redis) introduces environment friction and setup fragility. Instead, H2 in-memory storage with JPA Specifications was implemented, and the Elasticsearch/Redis architecture was documented in `DESIGN.md` as the scale-up roadmap.
2. **Using Reactive / WebFlux Stack**:
   - AI proposed using Spring WebFlux for non-blocking I/O.
   - Decision: Rejected in favor of Spring MVC with Java 21 Virtual Threads (`spring.threads.virtual.enabled: true`). Virtual Threads deliver comparable high-concurrency throughput while preserving the standard imperative programming model, synchronous transactions (`@Transactional`), and easier maintainability.
3. **Unbounded Concurrency**:
   - AI initially suggested firing parallel streams without thread pool control.
   - Decision: Rejected because uncontrolled parallel streams use the shared common `ForkJoinPool`, which can cause thread starvation across unrelated components in a web application. A dedicated, bounded thread pool was created instead.
