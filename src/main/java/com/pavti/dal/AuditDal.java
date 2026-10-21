package com.pavti.dal;

import com.pavti.dataobject.audit.AuditLog;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Who did what, when and why (audit_logs). */
@Repository
public class AuditDal {

    private final DataClass db;

    public AuditDal(DataClass db) {
        this.db = db;
    }

    public void add(Long businessId, Long userId, String actionCode, String entityName, Long entityId, String details, String reason, String ip) {
        db.execute("sp_audit_add", businessId, userId, actionCode, entityName, entityId, details, reason, ip);
    }

    /** businessId null = everything (platform admin). */
    public List<AuditLog> getAll(Long businessId, int limit) {
        return db.getList("sp_audit_get_all", AuditLog.class, businessId, limit);
    }
}
