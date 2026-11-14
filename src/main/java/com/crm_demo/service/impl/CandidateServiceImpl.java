package com.crm_demo.service.impl;

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
import com.crm_demo.service.CandidateService;
import com.crm_demo.specification.CandidateSpecification;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CandidateServiceImpl implements CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateServiceImpl.class);

    private final CandidateRepository candidateRepository;
    private final CandidateBatchProcessor candidateBatchProcessor;
    private final ApplicationEventPublisher eventPublisher;

    public CandidateServiceImpl(
            CandidateRepository candidateRepository,
            CandidateBatchProcessor candidateBatchProcessor,
            ApplicationEventPublisher eventPublisher) {
        this.candidateRepository = candidateRepository;
        this.candidateBatchProcessor = candidateBatchProcessor;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public CandidateResponse createCandidate(CandidateRequest request) {
        log.info("Creating new candidate with email: {}", request.email());

        if (candidateRepository.existsByEmailIgnoreCase(request.email())) {
            log.warn("Cannot create candidate: Email '{}' already registered in system", request.email());
            throw new DuplicateResourceException("Candidate with email " + request.email() + " already exists");
        }

        Candidate candidate = new Candidate(
                request.firstName().trim(),
                request.lastName().trim(),
                request.email().trim().toLowerCase(),
                request.phone(),
                request.currentTitle(),
                request.yearsOfExperience(),
                request.expectedSalary(),
                request.location(),
                request.skills(),
                request.status() != null ? request.status() : CandidateStatus.APPLIED,
                request.summary()
        );

        Candidate saved = candidateRepository.save(candidate);
        log.info("Successfully persisted candidate with ID: {}", saved.getId());

        // Publish domain event for asynchronous audit logging and indexing
        eventPublisher.publishEvent(new CandidateCreatedEvent(saved.getId(), saved.getEmail()));

        return CandidateResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateResponse getCandidateById(Long id) {
        log.debug("Fetching candidate with ID: {}", id);
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Candidate retrieval failed: No record found with ID {}", id);
                    return new ResourceNotFoundException("Candidate not found with id: " + id);
                });
        return CandidateResponse.fromEntity(candidate);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CandidateResponse> searchAndFilter(CandidateFilterCriteria criteria, Pageable pageable) {
        log.debug("Building search specification for criteria: {}", criteria);
        Specification<Candidate> spec = CandidateSpecification.buildSpecification(criteria);
        Page<CandidateResponse> page = candidateRepository.findAll(spec, pageable).map(CandidateResponse::fromEntity);
        log.debug("Specification query returned {} results on page {}", page.getNumberOfElements(), page.getNumber());
        return page;
    }

    @Override
    @Transactional
    public CandidateResponse updateCandidate(Long id, CandidateRequest request) {
        log.info("Updating candidate with ID: {}", id);
        Candidate existing = candidateRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Candidate update failed: No record found with ID {}", id);
                    return new ResourceNotFoundException("Candidate not found with id: " + id);
                });

        // Check if updating email to another existing candidate's email
        if (candidateRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            log.warn("Cannot update candidate ID {}: Email '{}' already belongs to another candidate", id, request.email());
            throw new DuplicateResourceException("Candidate with email " + request.email() + " already exists");
        }

        CandidateStatus oldStatus = existing.getStatus();
        CandidateStatus newStatus = request.status() != null ? request.status() : oldStatus;

        existing.setFirstName(request.firstName().trim());
        existing.setLastName(request.lastName().trim());
        existing.setEmail(request.email().trim().toLowerCase());
        existing.setPhone(request.phone());
        existing.setCurrentTitle(request.currentTitle());
        existing.setYearsOfExperience(request.yearsOfExperience());
        existing.setExpectedSalary(request.expectedSalary());
        existing.setLocation(request.location());
        existing.setSkills(request.skills());
        existing.setStatus(newStatus);
        existing.setSummary(request.summary());

        Candidate updated = candidateRepository.save(existing);
        log.info("Successfully saved updates for candidate ID: {}", id);

        // If candidate status transitioned, publish status change event
        if (oldStatus != newStatus) {
            log.info("Candidate ID {} status transitioned from {} to {}", id, oldStatus, newStatus);
            eventPublisher.publishEvent(new CandidateStatusChangedEvent(id, oldStatus, newStatus));
        }

        return CandidateResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteCandidate(Long id) {
        log.info("Deleting candidate with ID: {}", id);
        if (!candidateRepository.existsById(id)) {
            log.warn("Candidate deletion failed: No record found with ID {}", id);
            throw new ResourceNotFoundException("Candidate not found with id: " + id);
        }
        candidateRepository.deleteById(id);
        log.info("Successfully deleted candidate with ID: {}", id);
    }

    @Override
    public BulkImportResponse importCandidatesInBulk(List<CandidateRequest> requests) {
        log.info("Initiating multi-threaded bulk import for {} candidates", requests.size());
        return candidateBatchProcessor.processConcurrently(requests);
    }

    @Override
    public BulkImportResponse importCandidatesFromCsv(InputStream inputStream) {
        log.info("Parsing CSV input stream for bulk candidate import");
        List<CandidateRequest> requests = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser csvParser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : csvParser) {
                String firstName = record.isSet("firstName") ? record.get("firstName") : null;
                String lastName = record.isSet("lastName") ? record.get("lastName") : null;
                String email = record.isSet("email") ? record.get("email") : null;
                String phone = record.isSet("phone") ? record.get("phone") : null;
                String currentTitle = record.isSet("currentTitle") ? record.get("currentTitle") : null;

                Integer experience = 0;
                if (record.isSet("yearsOfExperience") && !record.get("yearsOfExperience").isBlank()) {
                    experience = Integer.parseInt(record.get("yearsOfExperience").trim());
                }

                BigDecimal salary = null;
                if (record.isSet("expectedSalary") && !record.get("expectedSalary").isBlank()) {
                    salary = new BigDecimal(record.get("expectedSalary").trim());
                }

                String location = record.isSet("location") ? record.get("location") : null;

                Set<String> skills = new HashSet<>();
                if (record.isSet("skills") && !record.get("skills").isBlank()) {
                    skills = Arrays.stream(record.get("skills").split(";"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());
                }

                CandidateStatus status = CandidateStatus.APPLIED;
                if (record.isSet("status") && !record.get("status").isBlank()) {
                    try {
                        status = CandidateStatus.valueOf(record.get("status").trim().toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        status = CandidateStatus.APPLIED;
                    }
                }

                String summary = record.isSet("summary") ? record.get("summary") : null;

                requests.add(new CandidateRequest(
                        firstName, lastName, email, phone, currentTitle,
                        experience, salary, location, skills, status, summary
                ));
            }
        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to parse CSV file: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to parse candidate CSV file: " + e.getMessage());
        }

        return candidateBatchProcessor.processConcurrently(requests);
    }
}
