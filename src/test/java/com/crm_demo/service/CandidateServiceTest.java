package com.crm_demo.service;

import com.crm_demo.dto.BulkImportResponse;
import com.crm_demo.dto.CandidateFilterCriteria;
import com.crm_demo.dto.CandidateRequest;
import com.crm_demo.dto.CandidateResponse;
import com.crm_demo.entity.Candidate;
import com.crm_demo.entity.CandidateStatus;
import com.crm_demo.event.CandidateCreatedEvent;
import com.crm_demo.event.CandidateStatusChangedEvent;
import com.crm_demo.exception.BadRequestException;
import com.crm_demo.exception.DuplicateResourceException;
import com.crm_demo.exception.ResourceNotFoundException;
import com.crm_demo.repository.CandidateRepository;
import com.crm_demo.service.impl.CandidateBatchProcessor;
import com.crm_demo.service.impl.CandidateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private CandidateBatchProcessor candidateBatchProcessor;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CandidateServiceImpl candidateService;

    private Candidate sampleCandidate;
    private CandidateRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleCandidate = new Candidate(
                "Jane", "Doe", "jane.doe@example.com", "+1-555-9999",
                "Backend Engineer", 5, new BigDecimal("120000.00"),
                "Seattle, WA", Set.of("Java", "Spring Boot", "SQL"),
                CandidateStatus.APPLIED, "Passionate Java developer"
        );
        sampleCandidate.setId(1L);

        sampleRequest = new CandidateRequest(
                "Jane", "Doe", "jane.doe@example.com", "+1-555-9999",
                "Backend Engineer", 5, new BigDecimal("120000.00"),
                "Seattle, WA", Set.of("Java", "Spring Boot", "SQL"),
                CandidateStatus.APPLIED, "Passionate Java developer"
        );
    }

    @Test
    @DisplayName("Create Candidate - Should succeed and publish CandidateCreatedEvent")
    void createCandidate_success() {
        when(candidateRepository.existsByEmailIgnoreCase("jane.doe@example.com")).thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> {
            Candidate toSave = invocation.getArgument(0);
            toSave.setId(1L);
            return toSave;
        });

        CandidateResponse response = candidateService.createCandidate(sampleRequest);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("jane.doe@example.com");

        verify(eventPublisher, times(1)).publishEvent(any(CandidateCreatedEvent.class));
    }

    @Test
    @DisplayName("Create Candidate - Duplicate email should throw DuplicateResourceException")
    void createCandidate_duplicateEmail_throwsException() {
        when(candidateRepository.existsByEmailIgnoreCase("jane.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> candidateService.createCandidate(sampleRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(candidateRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Get Candidate by ID - Should return CandidateResponse when found")
    void getCandidateById_found() {
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(sampleCandidate));

        CandidateResponse response = candidateService.getCandidateById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.firstName()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("Get Candidate by ID - Should throw ResourceNotFoundException when not found")
    void getCandidateById_notFound() {
        when(candidateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.getCandidateById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Update Candidate - Status change should publish CandidateStatusChangedEvent")
    void updateCandidate_statusTransition_publishesEvent() {
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(sampleCandidate));
        when(candidateRepository.existsByEmailIgnoreCaseAndIdNot("jane.doe@example.com", 1L)).thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateRequest updateRequest = new CandidateRequest(
                "Jane", "Doe", "jane.doe@example.com", "+1-555-9999",
                "Senior Backend Engineer", 6, new BigDecimal("140000.00"),
                "Seattle, WA", Set.of("Java", "Spring Boot", "AWS"),
                CandidateStatus.INTERVIEWED, "Promoted to interview"
        );

        CandidateResponse response = candidateService.updateCandidate(1L, updateRequest);

        assertThat(response.status()).isEqualTo(CandidateStatus.INTERVIEWED);
        assertThat(response.currentTitle()).isEqualTo("Senior Backend Engineer");

        ArgumentCaptor<CandidateStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(CandidateStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        CandidateStatusChangedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.candidateId()).isEqualTo(1L);
        assertThat(capturedEvent.oldStatus()).isEqualTo(CandidateStatus.APPLIED);
        assertThat(capturedEvent.newStatus()).isEqualTo(CandidateStatus.INTERVIEWED);
    }

    @Test
    @DisplayName("Delete Candidate - Should delete when candidate exists")
    void deleteCandidate_success() {
        when(candidateRepository.existsById(1L)).thenReturn(true);

        candidateService.deleteCandidate(1L);

        verify(candidateRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Delete Candidate - Should throw ResourceNotFoundException when ID does not exist")
    void deleteCandidate_notFound() {
        when(candidateRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> candidateService.deleteCandidate(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(candidateRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Search & Filter Candidates - Should return page of CandidateResponse")
    void searchAndFilter_success() {
        Pageable pageable = PageRequest.of(0, 10);
        CandidateFilterCriteria criteria = new CandidateFilterCriteria(
                "Java", Set.of("Java"), CandidateStatus.APPLIED, 3, 10, "Seattle", null, null
        );

        when(candidateRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Candidate>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleCandidate), pageable, 1));

        Page<CandidateResponse> result = candidateService.searchAndFilter(criteria, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).firstName()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("Bulk Import - Delegates to CandidateBatchProcessor")
    void importCandidatesInBulk_delegates() {
        BulkImportResponse expected = new BulkImportResponse(1, 1, 0, 10L, "Multi-Threaded", Collections.emptyList());
        when(candidateBatchProcessor.processConcurrently(any())).thenReturn(expected);

        BulkImportResponse actual = candidateService.importCandidatesInBulk(List.of(sampleRequest));

        assertThat(actual).isEqualTo(expected);
        verify(candidateBatchProcessor).processConcurrently(any());
    }

    @Test
    @DisplayName("CSV Import - Parses CSV input and invokes batch processor")
    void importCandidatesFromCsv_success() {
        String csvContent = "firstName,lastName,email,phone,currentTitle,yearsOfExperience,expectedSalary,location,skills,status,summary\n" +
                "Jessica,Taylor,jessica@example.com,+1-555-1234,Lead Engineer,8,130000.00,Remote,Java;Spring;PostgreSQL,OFFERED,Analyst";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        BulkImportResponse expected = new BulkImportResponse(1, 1, 0, 15L, "Multi-Threaded", Collections.emptyList());

        when(candidateBatchProcessor.processConcurrently(any())).thenReturn(expected);

        BulkImportResponse actual = candidateService.importCandidatesFromCsv(inputStream);

        assertThat(actual).isEqualTo(expected);
        verify(candidateBatchProcessor).processConcurrently(any());
    }

    @Test
    @DisplayName("CSV Import - Malformed CSV throws BadRequestException")
    void importCandidatesFromCsv_malformed_throwsBadRequest() {
        ByteArrayInputStream closedStream = new ByteArrayInputStream(new byte[0]);
        // A stream that cannot be read properly or empty
        // Let's pass a closed stream or an unparseable input
        ByteArrayInputStream corruptStream = new ByteArrayInputStream("firstName,yearsOfExperience\nAda,not_a_number".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> candidateService.importCandidatesFromCsv(corruptStream))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Create Candidate - Null status defaults to APPLIED")
    void createCandidate_nullStatus_defaultsToApplied() {
        CandidateRequest req = new CandidateRequest(
                "Jane", "Doe", "jane.default@example.com", null,
                "Software Engineer", 3, null, "Seattle, WA",
                Set.of("Java", "Spring", "SQL"), null, null
        );

        when(candidateRepository.existsByEmailIgnoreCase(req.email())).thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
            Candidate c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        CandidateResponse res = candidateService.createCandidate(req);

        assertThat(res.status()).isEqualTo(CandidateStatus.APPLIED);
    }

    @Test
    @DisplayName("Update Candidate - Same status does not publish status change event")
    void updateCandidate_sameStatus_noEventPublished() {
        Candidate existing = new Candidate(
                "Jane", "Doe", "jane.doe@example.com", null,
                "Engineer", 5, null, "Austin, TX",
                Set.of("Java", "Spring", "Docker"), CandidateStatus.SCREENING, null
        );
        existing.setId(1L);

        when(candidateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(candidateRepository.existsByEmailIgnoreCaseAndIdNot(any(), eq(1L))).thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0));

        CandidateRequest updateReq = new CandidateRequest(
                "Jane", "Doe", "jane.doe@example.com", null,
                "Senior Engineer", 6, null, "Austin, TX",
                Set.of("Java", "Spring", "Docker"), null, null
        );

        CandidateResponse response = candidateService.updateCandidate(1L, updateReq);

        assertThat(response.status()).isEqualTo(CandidateStatus.SCREENING);
        verify(eventPublisher, never()).publishEvent(any(CandidateStatusChangedEvent.class));
    }

    @Test
    @DisplayName("Update Candidate - Not found throws ResourceNotFoundException")
    void updateCandidate_notFound_throwsException() {
        when(candidateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.updateCandidate(999L, sampleRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Candidate not found with id: 999");
    }

    @Test
    @DisplayName("Update Candidate - Duplicate email for another candidate throws DuplicateResourceException")
    void updateCandidate_duplicateEmail_throwsException() {
        Candidate existing = new Candidate(
                "Jane", "Doe", "jane.doe@example.com", null,
                "Engineer", 5, null, "Austin, TX",
                Set.of("Java", "Spring", "Docker"), CandidateStatus.SCREENING, null
        );
        existing.setId(1L);

        when(candidateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(candidateRepository.existsByEmailIgnoreCaseAndIdNot("other.taken@example.com", 1L)).thenReturn(true);

        CandidateRequest req = new CandidateRequest(
                "Jane", "Doe", "other.taken@example.com", null,
                "Engineer", 5, null, "Austin, TX",
                Set.of("Java", "Spring", "Docker"), CandidateStatus.SCREENING, null
        );

        assertThatThrownBy(() -> candidateService.updateCandidate(1L, req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("CSV Import - Invalid status string falls back to APPLIED")
    void importCandidatesFromCsv_invalidStatus_defaultsToApplied() {
        String csvContent = "firstName,lastName,email,phone,currentTitle,yearsOfExperience,expectedSalary,location,skills,status,summary\n" +
                "Jessica,Taylor,jessica.fallback@example.com,+1-555-1234,Lead Engineer,8,130000.00,Remote,Java;Spring;PostgreSQL,INVALID_STATUS,Analyst";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        BulkImportResponse expected = new BulkImportResponse(1, 1, 0, 15L, "Multi-Threaded", Collections.emptyList());

        ArgumentCaptor<List<CandidateRequest>> captor = ArgumentCaptor.forClass(List.class);
        when(candidateBatchProcessor.processConcurrently(captor.capture())).thenReturn(expected);

        candidateService.importCandidatesFromCsv(inputStream);

        List<CandidateRequest> captured = captor.getValue();
        assertThat(captured.get(0).status()).isEqualTo(CandidateStatus.APPLIED);
    }
}
