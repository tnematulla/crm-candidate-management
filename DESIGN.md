# Technical Design Document: Candidate Management Service

## 1. Executive Summary

This document details the architectural decisions, data model, concurrency strategy, and scalability roadmap for the Candidate Management Service (`crm_demo`). The system is designed to provide candidate lifecycle operations, dynamic multi-criteria search, and asynchronous bulk data ingestion for modern recruitment platforms.

---

## 2. Architecture & Design Principles

### 2.1 Layered Architecture
The service adheres to standard clean layered architecture with clear separation of concerns:
- Web / Controller Layer: Exposes versioned REST APIs (`/api/v1/candidates`), validates input constraints, and marshals requests/responses.
- Service Layer: Encapsulates business logic, enforces email uniqueness invariants, and coordinates transactional workflows.
- Persistence Layer: Spring Data JPA abstractions over relational storage with dynamic specification criteria builders.
- Asynchronous & Event Layer: Decoupled event publishing (`CandidateCreatedEvent`, `CandidateStatusChangedEvent`) and multi-threaded batch processors.

### 2.2 Domain Model & Data Schema
The primary entity is `Candidate`, representing job applicants:
- Identity & Contact: `id` (primary key), `firstName`, `lastName`, `email` (unique index), `phone`, `location`.
- Professional Attributes: `currentTitle`, `yearsOfExperience` (indexed for range queries), `expectedSalary`, `skills` (normalized secondary collection).
- Lifecycle & Audit: `status` (indexed enum), `summary`, `createdAt`, `updatedAt`.

Supporting entity `AuditLog` captures state changes and background thread details asynchronously:
- `id`, `candidateId`, `eventType`, `description`, `performedBy`, `threadName`, `timestamp`.

### 2.3 Dynamic Multi-Criteria Search
Rather than constructing brittle static query methods, dynamic filtering is implemented using Spring Data JPA's Criteria API via `CandidateSpecification`:
- Full-text keyword matching across `firstName`, `lastName`, `email`, `currentTitle`, and `summary` using case-insensitive SQL `LIKE` clauses.
- Range filters on `yearsOfExperience` and `expectedSalary`.
- Skill matching using `INNER JOIN` against the collection table with distinct query projection.
- Native integration with Spring Data `Pageable` to enforce limit/offset pagination and index-backed sorting, protecting database memory.

---

## 3. Concurrency & Multi-Threading Architecture

Concurrency was introduced to address concrete performance bottlenecks rather than added arbitrarily:

### 3.1 Java 21 Project Loom (Virtual Threads)
By configuring `spring.threads.virtual.enabled: true`, the embedded Tomcat container serves each HTTP request on a lightweight virtual thread. When an I/O operation (database query, disk read) blocks, the underlying platform carrier thread is yielded, allowing thousands of concurrent client connections with negligible memory overhead compared to traditional platform thread pools.

### 3.2 Partitioned Parallel Batch Ingestion (`CandidateBatchProcessor`)
Importing thousands of candidates via CSV or JSON is an I/O and CPU intensive task. To maximize throughput:
1. Candidate requests are partitioned into chunks (default 50 records per chunk).
2. Each chunk is dispatched as an independent task via `CompletableFuture.supplyAsync()` to a dedicated `ThreadPoolTaskExecutor` (`batchTaskExecutor`).
3. Chunks execute concurrently on worker threads (`batch-worker-1`, `batch-worker-2`, etc.).
4. Results and per-record errors are aggregated non-blockingly using `CompletableFuture.allOf()`.
5. Failures on individual records (e.g., malformed email or duplicate constraint) are isolated so valid candidates in the batch are committed without aborting the entire upload.

### 3.3 Asynchronous Domain Events (`@Async`)
Operations that modify candidate state publish domain events. The event listener `CandidateEventListener` runs asynchronously on a segregated `auditTaskExecutor` thread pool. This ensures:
- Primary CRUD response latency remains minimal.
- Database write locks on candidate tables are committed immediately without waiting for audit logging or notification dispatch.

---

## 4. Error Handling & Clean Code Standards

1. Jakarta Bean Validation: Enforces constraints at controller entry points (`@NotBlank`, `@Email`, `@Size`, `@Min`).
2. Uniform Error Representation: RFC 7807-compliant `ErrorResponse` containing timestamp, HTTP status, error description, request URI, and field-level validation breakdowns.
3. Centralized Exception Handling: `GlobalExceptionHandler` with `@RestControllerAdvice` maps domain exceptions (`ResourceNotFoundException` -> 404, `DuplicateResourceException` -> 409, `BadRequestException` -> 400) cleanly.
4. SonarQube & Clean Code Compliance:
   - DTOs implemented as immutable Java 21 Records.
   - Serial version UIDs on custom `RuntimeException` classes.
   - SLF4J parameterized logging avoiding string concatenation overhead.
   - No generic exception swallowing; specific checked and runtime exceptions caught and logged.

---

## 5. Scalability Evolution Roadmap

To evolve this service to handle millions of candidates and thousands of concurrent recruiters, the following incremental architecture evolution is proposed:

### 5.1 Storage Layer Evolution
- Relational Database Migration: Transition from embedded H2 to managed PostgreSQL (e.g., AWS RDS Aurora PostgreSQL).
- Primary-Replica Topology: Route write traffic (CRUD, batch imports) to the primary node and read traffic (candidate searches, profile views) across multiple read replicas via Spring's `AbstractRoutingDataSource`.
- Table Partitioning: Partition the `candidates` table by registration year or geographic region to maintain predictable B-Tree index depths.
- Connection Pooling: Tune HikariCP connection pool parameters based on server CPU carrier threads and database I/O limits.

### 5.2 Search Engine Offloading (Elasticsearch / OpenSearch)
While JPA Specifications work well for small-to-medium datasets, complex full-text search, fuzzy matching, and multi-facet filtering at scale can saturate relational database CPU.
- Offload candidate searching to an Elasticsearch cluster.
- Implement Change Data Capture (CDC) using Debezium on PostgreSQL write-ahead logs (WAL) streaming into Apache Kafka.
- A consumer microservice indexes candidate documents in real time into Elasticsearch, allowing sub-50ms search latency across millions of records with typo-tolerance and skill synonyms.

### 5.3 Distributed Caching Layer (Redis)
- Cache frequent lookups (candidate profiles by ID, standard taxonomy tables) in Redis with an appropriate Time-To-Live (TTL).
- Use cache-aside pattern with automatic cache invalidation triggered on `CandidateUpdatedEvent`.
- Implement distributed rate limiting (Token Bucket via Redis) to prevent scraping and API abuse.

### 5.4 Distributed Event Streaming & Batch Queuing (Kafka)
- Transition the in-process `CandidateBatchProcessor` to a distributed queue using Apache Kafka.
- Bulk uploads produce messages to a `candidate-import-jobs` topic.
- A cluster of horizontally scaled worker instances consume from partitioned topics, enabling linear ingestion throughput scaling up to tens of thousands of records per second without impacting the API gateway.

### 5.5 Observability & Security
- Distributed Tracing: Integrate OpenTelemetry and Micrometer with Prometheus and Grafana for latency tracing and JVM metrics.
- Security: Secure API endpoints using OAuth 2.0 / OpenID Connect (Keycloak or Okta) with role-based access control (`ROLE_RECRUITER`, `ROLE_ADMIN`).
