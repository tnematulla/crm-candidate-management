package com.crm_demo.specification;

import com.crm_demo.dto.CandidateFilterCriteria;
import com.crm_demo.entity.Candidate;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CandidateSpecification {

    private CandidateSpecification() {
        // Utility class
    }

    public static Specification<Candidate> buildSpecification(CandidateFilterCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria == null) {
                return cb.conjunction();
            }

            // Keyword search across full name, email, currentTitle, and summary
            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                Predicate firstNameMatch = cb.like(cb.lower(root.get("firstName")), pattern);
                Predicate lastNameMatch = cb.like(cb.lower(root.get("lastName")), pattern);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), pattern);
                Predicate titleMatch = cb.like(cb.lower(root.get("currentTitle")), pattern);
                Predicate summaryMatch = cb.like(cb.lower(root.get("summary")), pattern);

                predicates.add(cb.or(firstNameMatch, lastNameMatch, emailMatch, titleMatch, summaryMatch));
            }

            // Status filter
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }

            // Experience range
            if (criteria.minExperience() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("yearsOfExperience"), criteria.minExperience()));
            }
            if (criteria.maxExperience() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("yearsOfExperience"), criteria.maxExperience()));
            }

            // Location filter
            if (criteria.location() != null && !criteria.location().isBlank()) {
                String locPattern = "%" + criteria.location().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("location")), locPattern));
            }

            // Salary range
            if (criteria.minSalary() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expectedSalary"), criteria.minSalary()));
            }
            if (criteria.maxSalary() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expectedSalary"), criteria.maxSalary()));
            }

            // Skills filter
            if (criteria.skills() != null && !criteria.skills().isEmpty()) {
                if (query != null) {
                    query.distinct(true);
                }
                Join<Candidate, String> skillsJoin = root.join("skills", JoinType.INNER);
                List<Predicate> skillPredicates = new ArrayList<>();
                for (String skill : criteria.skills()) {
                    if (skill != null && !skill.isBlank()) {
                        skillPredicates.add(cb.equal(cb.lower(skillsJoin), skill.trim().toLowerCase(Locale.ROOT)));
                    }
                }
                if (!skillPredicates.isEmpty()) {
                    predicates.add(cb.or(skillPredicates.toArray(new Predicate[0])));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
