package com.crm_demo.controller;

import com.crm_demo.dto.BulkImportResponse;
import com.crm_demo.dto.CandidateFilterCriteria;
import com.crm_demo.dto.CandidateRequest;
import com.crm_demo.dto.CandidateResponse;
import com.crm_demo.entity.CandidateStatus;
import com.crm_demo.exception.BadRequestException;
import com.crm_demo.service.CandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/candidates")
@Tag(name = "Candidates", description = "Endpoints for candidate management, search, filtering, and bulk ingestion")
public class CandidateController {

    private static final Logger log = LoggerFactory.getLogger(CandidateController.class);

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @PostMapping
    @Operation(summary = "Create candidate", description = "Creates a new candidate record with contact details and skills")
    public ResponseEntity<CandidateResponse> createCandidate(@Valid @RequestBody CandidateRequest request) {
        log.info("REST request to create candidate: {}", request.email());
        CandidateResponse response = candidateService.createCandidate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get candidate by ID", description = "Retrieves a single candidate record by their unique ID")
    public ResponseEntity<CandidateResponse> getCandidateById(@PathVariable Long id) {
        log.info("REST request to get candidate ID: {}", id);
        CandidateResponse response = candidateService.getCandidateById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Search and filter candidates", description = "Dynamic multi-criteria search and filtering with pagination and sorting")
    public ResponseEntity<Page<CandidateResponse>> searchAndFilter(
            @Parameter(description = "Keyword matching name, email, title, or summary")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Filter by candidate skill tags")
            @RequestParam(required = false) Set<String> skills,

            @Parameter(description = "Filter by candidate recruitment status")
            @RequestParam(required = false) CandidateStatus status,

            @Parameter(description = "Minimum years of experience")
            @RequestParam(required = false) Integer minExperience,

            @Parameter(description = "Maximum years of experience")
            @RequestParam(required = false) Integer maxExperience,

            @Parameter(description = "Filter by location substring")
            @RequestParam(required = false) String location,

            @Parameter(description = "Minimum expected salary")
            @RequestParam(required = false) BigDecimal minSalary,

            @Parameter(description = "Maximum expected salary")
            @RequestParam(required = false) BigDecimal maxSalary,

            @Parameter(description = "Page number (zero-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "Sort criteria formatted as property,(asc|desc)")
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Sort sortObj = parseSortParameter(sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        CandidateFilterCriteria criteria = new CandidateFilterCriteria(
                keyword, skills, status, minExperience, maxExperience, location, minSalary, maxSalary
        );

        Page<CandidateResponse> results = candidateService.searchAndFilter(criteria, pageable);
        return ResponseEntity.ok(results);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update candidate", description = "Updates an existing candidate's information")
    public ResponseEntity<CandidateResponse> updateCandidate(
            @PathVariable Long id,
            @Valid @RequestBody CandidateRequest request) {
        log.info("REST request to update candidate ID: {}", id);
        CandidateResponse response = candidateService.updateCandidate(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete candidate", description = "Deletes a candidate record by ID")
    public ResponseEntity<Void> deleteCandidate(@PathVariable Long id) {
        log.info("REST request to delete candidate ID: {}", id);
        candidateService.deleteCandidate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/batch")
    @Operation(summary = "Bulk import candidates (JSON)", description = "Asynchronously processes a list of candidates in parallel chunks")
    public ResponseEntity<BulkImportResponse> importCandidatesInBulk(@RequestBody List<CandidateRequest> requests) {
        log.info("REST request for JSON bulk import of {} candidates", requests.size());
        BulkImportResponse response = candidateService.importCandidatesInBulk(requests);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/bulk-upload-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import candidates (CSV)", description = "Parses and concurrently ingests candidates from a CSV file")
    public ResponseEntity<BulkImportResponse> importCandidatesFromCsv(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded CSV file must not be empty");
        }

        try {
            log.info("REST request for CSV bulk upload: filename={}, size={} bytes", file.getOriginalFilename(), file.getSize());
            BulkImportResponse response = candidateService.importCandidatesFromCsv(file.getInputStream());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to read uploaded file", e);
            throw new BadRequestException("Could not read uploaded CSV file: " + e.getMessage());
        }
    }

    private Sort parseSortParameter(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sortParam.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
