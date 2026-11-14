package com.university.erp.service.admin;
import com.university.erp.entity.AuditLog;
import com.university.erp.entity.User;
import com.university.erp.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class AdminAuditService {
    private final AuditLogRepository repository;
    public void record(User actor, String action, String entity, Long id) {
        repository.save(AuditLog.builder().user(actor).action(action).entityType(entity).entityId(id).build());
    }
}
