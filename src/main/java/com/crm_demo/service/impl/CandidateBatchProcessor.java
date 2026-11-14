package com.crm_demo.service.impl;

import com.crm_demo.dto.BulkImportResponse;
import com.crm_demo.dto.CandidateRequest;
import com.crm_demo.entity.Candidate;
import com.crm_demo.entity.CandidateStatus;
import com.crm_demo.repository.CandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class CandidateBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(CandidateBatchProcessor.class);

    private final CandidateRepository candidateRepository;
    private final Executor batchTaskExecutor;
    private final int chunkSize;

    public CandidateBatchProcessor(
            CandidateRepository candidateRepository,
            @Qualifier("batchTaskExecutor") Executor batchTaskExecutor,
            @Value("${crm.batch.chunk-size:50}") int chunkSize) {
        this.candidateRepository = candidateRepository;
        this.batchTaskExecutor = batchTaskExecutor;
        this.chunkSize = chunkSize;
    }

    public BulkImportResponse processConcurrently(List<CandidateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new BulkImportResponse(0, 0, 0, 0L, "Multi-Threaded Batch Processor", Collections.emptyList());
        }

        long startTime = System.currentTimeMillis();
        int totalSubmitted = requests.size();

        // Partition candidate requests into manageable chunks
        List<List<CandidateRequest>> chunks = partition(requests, chunkSize);
        log.info("Splitting {} candidate requests into {} parallel chunks for processing", totalSubmitted, chunks.size());

        // Dispatch chunk processing asynchronously across worker threads
        List<CompletableFuture<ChunkResult>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.supplyAsync(() -> processChunk(chunk), batchTaskExecutor))
                .toList();

        // Await all chunk worker tasks to finish
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Aggregate chunk results
        int totalSuccess = 0;
        int totalFailed = 0;
        List<String> aggregatedErrors = new ArrayList<>();

        for (CompletableFuture<ChunkResult> future : futures) {
            ChunkResult chunkResult = future.join();
            totalSuccess += chunkResult.successCount();
            totalFailed += chunkResult.failedCount();
            aggregatedErrors.addAll(chunkResult.errors());
        }

        long executionTimeMs = System.currentTimeMillis() - startTime;
        log.info("Completed multi-threaded batch import in {} ms: {} succeeded, {} failed",
                executionTimeMs, totalSuccess, totalFailed);

        return new BulkImportResponse(
                totalSubmitted,
                totalSuccess,
                totalFailed,
                executionTimeMs,
                "Multi-Threaded Parallel Chunk Executor (" + chunks.size() + " worker tasks)",
                aggregatedErrors
        );
    }

    private ChunkResult processChunk(List<CandidateRequest> chunk) {
        String threadName = Thread.currentThread().getName();
        log.debug("Worker thread [{}] processing chunk of {} candidates", threadName, chunk.size());

        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();
        List<Candidate> entitiesToSave = new ArrayList<>();

        for (CandidateRequest req : chunk) {
            try {
                validateRequest(req);

                if (candidateRepository.existsByEmailIgnoreCase(req.email())) {
                    failedCount++;
                    log.warn("Worker [{}] duplicate email detected and skipped: {}", threadName, req.email());
                    errors.add("Duplicate email skipped: " + req.email());
                    continue;
                }

                Candidate candidate = new Candidate(
                        req.firstName().trim(),
                        req.lastName().trim(),
                        req.email().trim().toLowerCase(),
                        req.phone(),
                        req.currentTitle(),
                        req.yearsOfExperience(),
                        req.expectedSalary(),
                        req.location(),
                        req.skills(),
                        req.status() != null ? req.status() : CandidateStatus.APPLIED,
                        req.summary()
                );
                entitiesToSave.add(candidate);
                successCount++;
            } catch (RuntimeException ex) {
                failedCount++;
                String email = req != null && req.email() != null ? req.email() : "unknown";
                log.warn("Worker [{}] failed to process candidate [{}]: {}", threadName, email, ex.getMessage());
                errors.add("Failed to process candidate [" + email + "]: " + ex.getMessage());
            }
        }

        if (!entitiesToSave.isEmpty()) {
            candidateRepository.saveAll(entitiesToSave);
            log.info("Worker [{}] batch persisted {} candidate records", threadName, entitiesToSave.size());
        }

        return new ChunkResult(successCount, failedCount, errors);
    }

    private void validateRequest(CandidateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Candidate request payload cannot be null");
        }
        if (req.firstName() == null || req.firstName().isBlank()) {
            throw new IllegalArgumentException("First name is mandatory");
        }
        if (req.lastName() == null || req.lastName().isBlank()) {
            throw new IllegalArgumentException("Last name is mandatory");
        }
        if (req.email() == null || req.email().isBlank() || !req.email().contains("@")) {
            throw new IllegalArgumentException("Valid email is mandatory");
        }
        if (req.yearsOfExperience() == null || req.yearsOfExperience() < 0 || req.yearsOfExperience() > 80) {
            throw new IllegalArgumentException("Years of experience must be between 0 and 80 years");
        }
        if (req.currentTitle() == null || req.currentTitle().isBlank()) {
            throw new IllegalArgumentException("Current title is mandatory");
        }
        if (req.location() == null || req.location().isBlank()) {
            throw new IllegalArgumentException("Location is mandatory");
        }
        if (req.skills() == null || req.skills().size() < 3) {
            throw new IllegalArgumentException("At least 3 skills are required");
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    private record ChunkResult(int successCount, int failedCount, List<String> errors) {
    }
}
