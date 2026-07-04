package com.interview.assessment.service;

import com.interview.assessment.entity.AuditLog;
import com.interview.assessment.repository.AuditLogRepository;
import com.interview.assessment.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Module 6 (audit trail): records domain-level actions (create/update/delete/status change). */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void record(String entityName, Long entityId, String action, String details) {
        AuditLog log = new AuditLog();
        log.setEntityName(entityName);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setActor(CurrentUser.emailOrSystem());
        log.setDetails(details);
        auditLogRepository.save(log);
    }
}
