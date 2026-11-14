package com.crm_demo.service;

import com.crm_demo.dto.CandidateFilterCriteria;
import com.crm_demo.dto.CandidateRequest;
import com.crm_demo.dto.CandidateResponse;
import com.crm_demo.entity.Candidate;
import com.crm_demo.entity.CandidateStatus;
import com.crm_demo.event.CandidateCreatedEvent;
import com.crm_demo.event.CandidateStatusChangedEvent;
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

import java.math.BigDecimal;
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
                "Seattle, WA", Set.of("Java", "Spring Boot"),
                CandidateStatus.APPLIED, "Passionate Java developer"
        );
        sampleCandidate.setId(1L);

        sampleRequest = new CandidateRequest(
                "Jane", "Doe", "jane.doe@example.com", "+1-555-9999",
                "Backend Engineer", 5, new BigDecimal("120000.00"),
                "Seattle, WA", Set.of("Java", "Spring Boot"),
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
}
