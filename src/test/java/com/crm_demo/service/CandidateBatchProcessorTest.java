package com.crm_demo.service;

import com.crm_demo.dto.BulkImportResponse;
import com.crm_demo.dto.CandidateRequest;
import com.crm_demo.entity.CandidateStatus;
import com.crm_demo.repository.CandidateRepository;
import com.crm_demo.service.impl.CandidateBatchProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateBatchProcessorTest {

    @Mock
    private CandidateRepository candidateRepository;

    private ExecutorService testExecutor;
    private CandidateBatchProcessor batchProcessor;

    @BeforeEach
    void setUp() {
        testExecutor = Executors.newFixedThreadPool(4);
        // Chunk size of 10 to ensure multiple parallel chunks are generated
        batchProcessor = new CandidateBatchProcessor(candidateRepository, testExecutor, 10);
    }

    @AfterEach
    void tearDown() {
        if (testExecutor != null) {
            testExecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Concurrent Batch Import - Partitions requests and processes chunks across worker threads")
    void processConcurrently_multipleChunks_success() {
        when(candidateRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(candidateRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Create 35 requests -> 4 chunks (10, 10, 10, 5)
        List<CandidateRequest> requests = new ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            requests.add(new CandidateRequest(
                    "First" + i,
                    "Last" + i,
                    "candidate" + i + "@example.com",
                    "+1-555-01" + i,
                    "Software Engineer",
                    3,
                    new BigDecimal("95000.00"),
                    "City" + i,
                    Set.of("Java", "Spring Boot", "SQL"),
                    CandidateStatus.APPLIED,
                    "Summary " + i
            ));
        }

        BulkImportResponse response = batchProcessor.processConcurrently(requests);

        assertThat(response).isNotNull();
        assertThat(response.totalSubmitted()).isEqualTo(35);
        assertThat(response.totalSuccess()).isEqualTo(35);
        assertThat(response.totalFailed()).isZero();
        assertThat(response.errorMessages()).isEmpty();
        assertThat(response.processingMechanism()).contains("Multi-Threaded");

        // Verify saveAll was called for the chunks
        verify(candidateRepository, atLeastOnce()).saveAll(anyList());
    }

    @Test
    @DisplayName("Concurrent Batch Import - Skips duplicate emails and invalid records gracefully")
    void processConcurrently_withDuplicatesAndInvalidRecords() {
        // Mock candidate 2 as already existing
        when(candidateRepository.existsByEmailIgnoreCase("candidate2@example.com")).thenReturn(true);
        when(candidateRepository.existsByEmailIgnoreCase("candidate1@example.com")).thenReturn(false);

        List<CandidateRequest> requests = List.of(
                new CandidateRequest("Valid", "One", "candidate1@example.com", null, "Dev", 2, null, "Seattle", Set.of("Java", "Spring", "SQL"), null, null),
                new CandidateRequest("Duplicate", "Two", "candidate2@example.com", null, "Dev", 3, null, "Seattle", Set.of("Java", "Spring", "SQL"), null, null),
                new CandidateRequest("", "Invalid", "candidate3@example.com", null, "Dev", 4, null, "Seattle", Set.of("Java", "Spring", "SQL"), null, null) // blank first name
        );

        BulkImportResponse response = batchProcessor.processConcurrently(requests);

        assertThat(response.totalSubmitted()).isEqualTo(3);
        assertThat(response.totalSuccess()).isEqualTo(1);
        assertThat(response.totalFailed()).isEqualTo(2);
        assertThat(response.errorMessages()).hasSize(2);
    }

    @Test
    @DisplayName("Concurrent Batch Import - Null and empty input handling")
    void processConcurrently_nullAndEmptyInput() {
        BulkImportResponse nullResp = batchProcessor.processConcurrently(null);
        assertThat(nullResp.totalSubmitted()).isZero();
        assertThat(nullResp.totalSuccess()).isZero();

        BulkImportResponse emptyResp = batchProcessor.processConcurrently(List.of());
        assertThat(emptyResp.totalSubmitted()).isZero();
        assertThat(emptyResp.totalSuccess()).isZero();
    }

    @Test
    @DisplayName("Concurrent Batch Import - Comprehensive validation failures and empty saveAll branch")
    void processConcurrently_comprehensiveValidationFailures() {
        List<CandidateRequest> invalidRequests = new ArrayList<>();
        invalidRequests.add(null);
        invalidRequests.add(new CandidateRequest(null, "Last", "e1@a.com", null, "Title", 5, null, "Loc", Set.of("A", "B", "C"), CandidateStatus.APPLIED, null));
        invalidRequests.add(new CandidateRequest("First", null, "e2@a.com", null, "Title", 5, null, "Loc", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", " ", "e3@a.com", null, "Title", 5, null, "Loc", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", null, null, "Title", 5, null, "Loc", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", " ", null, "Title", 5, null, "Loc", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", "notanemail", null, "Title", 5, null, "Loc", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", "e4@a.com", null, "Title", null, null, "Loc", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", "e5@a.com", null, "Title", -1, null, "Loc", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", "e6@a.com", null, "Title", 81, null, "Loc", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", "e7@a.com", null, null, 5, null, "Loc", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", "e8@a.com", null, " ", 5, null, "Loc", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", "e9@a.com", null, "Title", 5, null, null, Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", "e10@a.com", null, "Title", 5, null, " ", Set.of("A", "B", "C"), null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", "e11@a.com", null, "Title", 5, null, "Loc", null, null, null));
        invalidRequests.add(new CandidateRequest("First", "Last", "e12@a.com", null, "Title", 5, null, "Loc", Set.of("A", "B"), null, null));

        BulkImportResponse response = batchProcessor.processConcurrently(invalidRequests);

        assertThat(response.totalSubmitted()).isEqualTo(invalidRequests.size());
        assertThat(response.totalSuccess()).isZero();
        assertThat(response.totalFailed()).isEqualTo(invalidRequests.size());
        assertThat(response.errorMessages()).hasSize(invalidRequests.size());
    }
}
