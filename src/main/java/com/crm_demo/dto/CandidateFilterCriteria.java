package com.crm_demo.dto;

import com.crm_demo.entity.CandidateStatus;

import java.math.BigDecimal;
import java.util.Set;

public record CandidateFilterCriteria(
        String keyword,
        Set<String> skills,
        CandidateStatus status,
        Integer minExperience,
        Integer maxExperience,
        String location,
        BigDecimal minSalary,
        BigDecimal maxSalary
) {
}
