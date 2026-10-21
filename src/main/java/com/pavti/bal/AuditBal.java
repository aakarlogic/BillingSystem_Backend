package com.pavti.bal;

import com.pavti.dal.AuditDal;
import com.pavti.dataobject.audit.AuditLog;
import com.pavti.filter.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Writes and reads the history (audit_logs). */
@Service
public class AuditBal {

    private final AuditDal auditDal;

    public AuditBal(AuditDal auditDal) {
        this.auditDal = auditDal;
    }

    public void log(CurrentUser user, String actionCode, String entityName, Long entityId, String details, String reason) {
        log(user.businessId(), user.userId(), actionCode, entityName, entityId, details, reason);
    }

    public void log(Long businessId, Long userId, String actionCode, String entityName, Long entityId, String details, String reason) {
        auditDal.add(businessId, userId, actionCode, entityName, entityId, details, reason, clientIp());
    }

    public List<AuditLog> getAll(CurrentUser user) {
        return auditDal.getAll(user.isPlatformAdmin() ? null : user.requireBusinessId(), 500);
    }

    public static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            HttpServletRequest r = a.getRequest();
            String fwd = r.getHeader("X-Forwarded-For");
            return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : r.getRemoteAddr();
        }
        return null;
    }
}
