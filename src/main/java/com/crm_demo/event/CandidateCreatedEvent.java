package com.crm_demo.event;

import java.time.Instant;

public record CandidateCreatedEvent(
        Long candidateId,
        String email,
        Instant timestamp
) {
    public CandidateCreatedEvent(Long candidateId, String email) {
        this(candidateId, email, Instant.now());
    }
}
