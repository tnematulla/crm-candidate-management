package com.crm_demo.repository;

import com.crm_demo.dto.CandidateFilterCriteria;
import com.crm_demo.entity.Candidate;
import com.crm_demo.entity.CandidateStatus;
import com.crm_demo.specification.CandidateSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CandidateRepositoryTest {

    @Autowired
    private CandidateRepository candidateRepository;

    @BeforeEach
    void setUp() {
        candidateRepository.deleteAll();

        Candidate c1 = new Candidate(
                "Michael", "Scott", "michael.clark@example.com", "+1-555-1111",
                "Regional Manager", 12, new BigDecimal("80000.00"),
                "Scranton, PA", Set.of("Leadership", "Sales"),
                CandidateStatus.INTERVIEWED, "Experienced branch manager"
        );

        Candidate c2 = new Candidate(
                "Dwight", "Schrute", "daniel.wright@example.com", "+1-555-2222",
                "Assistant to the Regional Manager", 8, new BigDecimal("75000.00"),
                "Scranton, PA", Set.of("Sales", "Java", "Security"),
                CandidateStatus.SCREENING, "Top salesman with technical acumen"
        );

        Candidate c3 = new Candidate(
                "Jim", "Halpert", "james.harper@example.com", "+1-555-3333",
                "Senior Account Executive", 6, new BigDecimal("70000.00"),
                "Philadelphia, PA", Set.of("Sales", "Client Relations"),
                CandidateStatus.APPLIED, "Strong communication skills"
        );

        candidateRepository.save(c1);
        candidateRepository.save(c2);
        candidateRepository.save(c3);
    }

    @Test
    @DisplayName("Repository - existsByEmailIgnoreCase should return true for existing email")
    void existsByEmailIgnoreCase_found() {
        boolean exists = candidateRepository.existsByEmailIgnoreCase("MICHAEL.CLARK@example.com");
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Repository - findByEmailIgnoreCase should return candidate")
    void findByEmailIgnoreCase_found() {
        Optional<Candidate> candidate = candidateRepository.findByEmailIgnoreCase("daniel.wright@example.com");
        assertThat(candidate).isPresent();
        assertThat(candidate.get().getFirstName()).isEqualTo("Dwight");
    }

    @Test
    @DisplayName("Specification - Filter by skill 'Java' and minExperience 5")
    void filterBySkillAndExperience() {
        CandidateFilterCriteria criteria = new CandidateFilterCriteria(
                null, Set.of("Java"), null, 5, null, null, null, null
        );

        Specification<Candidate> spec = CandidateSpecification.buildSpecification(criteria);
        Page<Candidate> results = candidateRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getFirstName()).isEqualTo("Dwight");
    }

    @Test
    @DisplayName("Specification - Keyword search matching summary or title")
    void filterByKeyword() {
        CandidateFilterCriteria criteria = new CandidateFilterCriteria(
                "branch manager", null, null, null, null, null, null, null
        );

        Specification<Candidate> spec = CandidateSpecification.buildSpecification(criteria);
        Page<Candidate> results = candidateRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getEmail()).isEqualTo("michael.clark@example.com");
    }

    @Test
    @DisplayName("Specification - Full coverage for all filter branches and edge cases")
    void filterAllBranches() {
        // 1. Null criteria
        Specification<Candidate> nullSpec = CandidateSpecification.buildSpecification(null);
        Page<Candidate> allResults = candidateRepository.findAll(nullSpec, PageRequest.of(0, 10));
        assertThat(allResults.getContent()).hasSize(3);

        // 2. Status, maxExperience, location, minSalary, maxSalary
        CandidateFilterCriteria criteria = new CandidateFilterCriteria(
                "   ", // blank keyword
                Set.of("Sales", "   "), // skill with blank
                CandidateStatus.SCREENING,
                null,
                10,
                "Scranton",
                new BigDecimal("70000.00"),
                new BigDecimal("80000.00")
        );
        Specification<Candidate> spec = CandidateSpecification.buildSpecification(criteria);
        Page<Candidate> results = candidateRepository.findAll(spec, PageRequest.of(0, 10));
        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getFirstName()).isEqualTo("Dwight");

        // 3. Blank location and skills with only blank tags
        CandidateFilterCriteria blankCriteria = new CandidateFilterCriteria(
                null,
                Set.of("   "),
                null,
                null,
                null,
                "   ",
                null,
                null
        );
        Specification<Candidate> blankSpec = CandidateSpecification.buildSpecification(blankCriteria);
        Page<Candidate> blankResults = candidateRepository.findAll(blankSpec, PageRequest.of(0, 10));
        assertThat(blankResults.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("Specification - Private constructor invocation for utility class")
    void privateConstructor() throws Exception {
        java.lang.reflect.Constructor<CandidateSpecification> constructor = CandidateSpecification.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CandidateSpecification instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}
