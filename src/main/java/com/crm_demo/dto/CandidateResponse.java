package com.crm_demo.dto;

import com.crm_demo.entity.Candidate;
import com.crm_demo.entity.CandidateStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

public record CandidateResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String currentTitle,
        Integer yearsOfExperience,
        BigDecimal expectedSalary,
        String location,
        Set<String> skills,
        CandidateStatus status,
        String summary,
        Instant createdAt,
        Instant updatedAt
) {
    public static CandidateResponse fromEntity(Candidate candidate) {
        if (candidate == null) {
            return null;
        }
        return new CandidateResponse(
                candidate.getId(),
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getEmail(),
                candidate.getPhone(),
                candidate.getCurrentTitle(),
                candidate.getYearsOfExperience(),
                candidate.getExpectedSalary(),
                candidate.getLocation(),
                candidate.getSkills() != null ? Collections.unmodifiableSet(candidate.getSkills()) : Collections.emptySet(),
                candidate.getStatus(),
                candidate.getSummary(),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt()
        );
    }
}
