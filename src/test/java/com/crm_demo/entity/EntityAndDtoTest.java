package com.crm_demo.entity;

import com.crm_demo.dto.CandidateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EntityAndDtoTest {

    @Test
    @DisplayName("Candidate entity getters, setters, lifecycle, equals and hashCode")
    void candidateEntityFullCoverage() {
        Candidate c1 = new Candidate();
        c1.setId(100L);
        c1.setFirstName("Jessica");
        c1.setLastName("Taylor");
        c1.setEmail("jessica@example.com");
        c1.setPhone("+1-555-1234");
        c1.setCurrentTitle("Chief Scientist");
        c1.setYearsOfExperience(10);
        c1.setExpectedSalary(new BigDecimal("150000.00"));
        c1.setLocation("London, UK");
        c1.setSkills(new HashSet<>(Set.of("Math", "Algorithms", "Logic")));
        c1.setStatus(CandidateStatus.OFFERED);
        c1.setSummary("Senior research and systems specialist");
        
        Instant now = Instant.now();
        c1.setCreatedAt(now);
        c1.setUpdatedAt(now);

        assertThat(c1.getId()).isEqualTo(100L);
        assertThat(c1.getFirstName()).isEqualTo("Jessica");
        assertThat(c1.getLastName()).isEqualTo("Taylor");
        assertThat(c1.getEmail()).isEqualTo("jessica@example.com");
        assertThat(c1.getPhone()).isEqualTo("+1-555-1234");
        assertThat(c1.getCurrentTitle()).isEqualTo("Chief Scientist");
        assertThat(c1.getYearsOfExperience()).isEqualTo(10);
        assertThat(c1.getExpectedSalary()).isEqualTo(new BigDecimal("150000.00"));
        assertThat(c1.getLocation()).isEqualTo("London, UK");
        assertThat(c1.getSkills()).contains("Math", "Algorithms", "Logic");
        assertThat(c1.getStatus()).isEqualTo(CandidateStatus.OFFERED);
        assertThat(c1.getSummary()).isEqualTo("Senior research and systems specialist");
        assertThat(c1.getCreatedAt()).isEqualTo(now);
        assertThat(c1.getUpdatedAt()).isEqualTo(now);

        // PrePersist & PreUpdate lifecycle
        Candidate cLifecycle = new Candidate();
        cLifecycle.onCreate();
        assertThat(cLifecycle.getCreatedAt()).isNotNull();
        assertThat(cLifecycle.getUpdatedAt()).isNotNull();
        cLifecycle.onUpdate();

        // Null skills setter
        c1.setSkills(null);
        assertThat(c1.getSkills()).isEmpty();

        // Equals & HashCode
        assertThat(c1.equals(c1)).isTrue();
        assertThat(c1.equals(null)).isFalse();
        assertThat(c1.equals("different object type")).isFalse();

        Candidate c2 = new Candidate();
        c2.setEmail("JESSICA@EXAMPLE.COM");
        assertThat(c1.equals(c2)).isTrue();
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());

        Candidate c3 = new Candidate();
        c3.setEmail("other@example.com");
        assertThat(c1.equals(c3)).isFalse();

        Candidate cNullEmail1 = new Candidate();
        Candidate cNullEmail2 = new Candidate();
        assertThat(cNullEmail1.equals(cNullEmail2)).isFalse();
        assertThat(cNullEmail1.hashCode()).isZero();
    }

    @Test
    @DisplayName("AuditLog entity getters, setters, lifecycle, equals and hashCode")
    void auditLogEntityFullCoverage() {
        AuditLog log1 = new AuditLog();
        log1.setId(50L);
        log1.setCandidateId(100L);
        log1.setEventType("STATUS_CHANGE");
        log1.setDescription("Candidate status changed");
        log1.setPerformedBy("SYSTEM");
        log1.setThreadName("audit-worker-1");

        Instant now = Instant.now();
        log1.setTimestamp(now);

        assertThat(log1.getId()).isEqualTo(50L);
        assertThat(log1.getCandidateId()).isEqualTo(100L);
        assertThat(log1.getEventType()).isEqualTo("STATUS_CHANGE");
        assertThat(log1.getDescription()).isEqualTo("Candidate status changed");
        assertThat(log1.getPerformedBy()).isEqualTo("SYSTEM");
        assertThat(log1.getThreadName()).isEqualTo("audit-worker-1");
        assertThat(log1.getTimestamp()).isEqualTo(now);

        // Lifecycle onCreate
        AuditLog newLog = new AuditLog();
        newLog.onCreate();
        assertThat(newLog.getTimestamp()).isNotNull();

        AuditLog logWithExistingTimestamp = new AuditLog();
        logWithExistingTimestamp.setTimestamp(now);
        logWithExistingTimestamp.onCreate();
        assertThat(logWithExistingTimestamp.getTimestamp()).isEqualTo(now);

        // Equals and HashCode
        assertThat(log1.equals(log1)).isTrue();
        assertThat(log1.equals(null)).isFalse();
        assertThat(log1.equals("string")).isFalse();

        AuditLog log2 = new AuditLog();
        log2.setId(50L);
        assertThat(log1.equals(log2)).isTrue();
        assertThat(log1.hashCode()).isEqualTo(log2.hashCode());

        AuditLog log3 = new AuditLog();
        log3.setId(51L);
        assertThat(log1.equals(log3)).isFalse();

        AuditLog logNullId = new AuditLog();
        assertThat(logNullId.equals(log1)).isFalse();
    }

    @Test
    @DisplayName("CandidateResponse DTO null handling and record mapping")
    void candidateResponseNullHandling() {
        assertThat(CandidateResponse.fromEntity(null)).isNull();

        Candidate candidate = new Candidate(
                "Emily", "Watson", "emily@example.com", null,
                "Platform Engineer", 12, null, "Arlington, VA",
                null, CandidateStatus.HIRED, null
        );
        candidate.setId(99L);
        candidate.onCreate();

        CandidateResponse response = CandidateResponse.fromEntity(candidate);
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.skills()).isEmpty();
        assertThat(response.phone()).isNull();
        assertThat(response.expectedSalary()).isNull();
        assertThat(response.summary()).isNull();
    }
}
