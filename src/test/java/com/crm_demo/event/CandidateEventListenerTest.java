package com.crm_demo.event;

import com.crm_demo.entity.AuditLog;
import com.crm_demo.entity.CandidateStatus;
import com.crm_demo.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CandidateEventListenerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private CandidateEventListener eventListener;

    @Test
    @DisplayName("handleCandidateCreated - persists audit log with thread name")
    void handleCandidateCreated_persistsAuditLog() {
        CandidateCreatedEvent event = new CandidateCreatedEvent(100L, "test@example.com");

        eventListener.handleCandidateCreated(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getCandidateId()).isEqualTo(100L);
        assertThat(saved.getEventType()).isEqualTo("CANDIDATE_CREATED");
        assertThat(saved.getDescription()).contains("test@example.com");
        assertThat(saved.getThreadName()).isNotBlank();
    }

    @Test
    @DisplayName("handleStatusChanged - persists audit log with old and new status")
    void handleStatusChanged_persistsAuditLog() {
        CandidateStatusChangedEvent event = new CandidateStatusChangedEvent(
                200L, CandidateStatus.APPLIED, CandidateStatus.HIRED
        );

        eventListener.handleStatusChanged(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getCandidateId()).isEqualTo(200L);
        assertThat(saved.getEventType()).isEqualTo("STATUS_CHANGED");
        assertThat(saved.getDescription()).contains("APPLIED to HIRED");
    }
}
