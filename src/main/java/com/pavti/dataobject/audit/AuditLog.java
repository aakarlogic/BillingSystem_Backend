package com.pavti.dataobject.audit;

/** One history row (React app: types/report.ts AuditLog). */
public record AuditLog(
        long auditLogId,
        Long businessId,
        String businessName,
        String userName,
        String roleCode,
        String actionCode,
        String entityName,
        String details,
        String reason,
        String actionDate) {}
