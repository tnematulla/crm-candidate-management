package com.crm_demo.event;

import com.crm_demo.entity.AuditLog;
import com.crm_demo.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class CandidateEventListener {

    private static final Logger log = LoggerFactory.getLogger(CandidateEventListener.class);

    private final AuditLogRepository auditLogRepository;

    public CandidateEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async("auditTaskExecutor")
    @EventListener
    public void handleCandidateCreated(CandidateCreatedEvent event) {
        String threadName = Thread.currentThread().getName();
        log.info("Processing CandidateCreatedEvent asynchronously on thread {}: Candidate ID {}",
                threadName, event.candidateId());

        AuditLog auditLog = new AuditLog(
                event.candidateId(),
                "CANDIDATE_CREATED",
                "Candidate created with email: " + event.email(),
                "SYSTEM",
                threadName
        );
        auditLogRepository.save(auditLog);
    }

    @Async("auditTaskExecutor")
    @EventListener
    public void handleStatusChanged(CandidateStatusChangedEvent event) {
        String threadName = Thread.currentThread().getName();
        log.info("Processing CandidateStatusChangedEvent asynchronously on thread {}: Candidate ID {}, Status: {} -> {}",
                threadName, event.candidateId(), event.oldStatus(), event.newStatus());

        AuditLog auditLog = new AuditLog(
                event.candidateId(),
                "STATUS_CHANGED",
                String.format("Candidate status changed from %s to %s", event.oldStatus(), event.newStatus()),
                "SYSTEM",
                threadName
        );
        auditLogRepository.save(auditLog);
    }
}
