package com.pavti.dal;

import com.pavti.dataobject.platform.Plan;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Subscription plans (how many people / tables a business may have). */
@Repository
public class PlanDal {

    private final DataClass db;

    public PlanDal(DataClass db) {
        this.db = db;
    }

    public List<Plan> getAll() {
        return db.getList("sp_plan_get_all", Plan.class);
    }

    public Optional<Plan> getById(long planId) {
        return getAll().stream().filter(p -> p.planId() == planId).findFirst();
    }

    public long save(Long planId, String planCode, String planName, String description, BigDecimal monthlyPrice, int maxUsers,
                     Integer maxTables, long by) {
        return db.getLong("sp_plan_save", planId, planCode, planName, description, monthlyPrice, maxUsers, maxTables, by);
    }

    public boolean inUse(long planId) {
        return db.getLong("sp_plan_in_use", planId) > 0;
    }

    public void delete(long planId, long by) {
        db.execute("sp_plan_delete", planId, by);
    }
}
