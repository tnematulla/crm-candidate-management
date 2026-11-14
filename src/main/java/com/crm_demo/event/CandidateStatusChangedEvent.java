package com.crm_demo.event;

import com.crm_demo.entity.CandidateStatus;

import java.time.Instant;

public record CandidateStatusChangedEvent(
        Long candidateId,
        CandidateStatus oldStatus,
        CandidateStatus newStatus,
        Instant timestamp
) {
    public CandidateStatusChangedEvent(Long candidateId, CandidateStatus oldStatus, CandidateStatus newStatus) {
        this(candidateId, oldStatus, newStatus, Instant.now());
    }
}
