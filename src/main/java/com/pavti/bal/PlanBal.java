package com.pavti.bal;

import static com.pavti.common.TextHelper.clean;

import com.pavti.common.ApiException;
import com.pavti.dal.PlanDal;
import com.pavti.dataobject.platform.Plan;
import com.pavti.dataobject.platform.PlanSaveRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.PageAction;
import com.pavti.common.TextHelper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlanBal {

    private final PlanDal planDal;
    private final RightsBal rights;
    private final AuditBal audit;

    public PlanBal(PlanDal planDal, RightsBal rights, AuditBal audit) {
        this.planDal = planDal;
        this.rights = rights;
        this.audit = audit;
    }

    public List<Plan> getAll() {
        return planDal.getAll();
    }

    public void save(CurrentUser user, PlanSaveRequest req) {
        boolean isNew = req.planId() == null || req.planId() == 0;
        rights.requirePage(user, "PLANS", isNew ? PageAction.ADD : PageAction.EDIT);
        String name = clean(req.planName());
        if (name.isEmpty()) throw ApiException.badRequest("Plan name is required.");
        if (req.maxUsers() == null || req.maxUsers() < 1) throw ApiException.badRequest("Allow at least 1 person.");
        BigDecimal price = TextHelper.money(req.monthlyPrice());
        if (price.signum() < 0) throw ApiException.badRequest("Price cannot be negative.");
        Integer maxTables = req.maxTables() == null || req.maxTables() <= 0 ? null : req.maxTables();
        String code = clean(clean(req.planCode()).isEmpty() ? name : req.planCode()).toUpperCase().replaceAll("\\s+", "_");
        if (!isNew) planDal.getById(req.planId()).orElseThrow(() -> ApiException.notFound("Plan not found."));
        else if (planDal.getAll().stream().anyMatch(p -> p.planCode().equals(code))) throw ApiException.conflict("This plan code already exists.");
        long id = planDal.save(isNew ? null : req.planId(), code, name, clean(req.description()), price, req.maxUsers(), maxTables,
                user.userId());
        audit.log(null, user.userId(), "PLAN_SAVE", "plans", id, name, null);
    }

    public void delete(CurrentUser user, long planId) {
        Plan p = planDal.getById(planId).orElseThrow(() -> ApiException.notFound("Plan not found."));
        if (planDal.inUse(planId)) throw ApiException.badRequest("A business uses this plan. Move it to another plan first.");
        planDal.delete(planId, user.userId());
        audit.log(null, user.userId(), "PLAN_DELETE", "plans", planId, p.planName(), null);
    }
}
