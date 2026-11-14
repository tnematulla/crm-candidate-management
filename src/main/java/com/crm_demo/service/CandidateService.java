package com.crm_demo.service;

import com.crm_demo.dto.BulkImportResponse;
import com.crm_demo.dto.CandidateFilterCriteria;
import com.crm_demo.dto.CandidateRequest;
import com.crm_demo.dto.CandidateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.InputStream;
import java.util.List;

public interface CandidateService {

    CandidateResponse createCandidate(CandidateRequest request);

    CandidateResponse getCandidateById(Long id);

    Page<CandidateResponse> searchAndFilter(CandidateFilterCriteria criteria, Pageable pageable);

    CandidateResponse updateCandidate(Long id, CandidateRequest request);

    void deleteCandidate(Long id);

    BulkImportResponse importCandidatesInBulk(List<CandidateRequest> requests);

    BulkImportResponse importCandidatesFromCsv(InputStream inputStream);
}
