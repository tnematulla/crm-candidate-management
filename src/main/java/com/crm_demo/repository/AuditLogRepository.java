package com.crm_demo.repository;

import com.crm_demo.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByCandidateIdOrderByTimestampDesc(Long candidateId);
}
