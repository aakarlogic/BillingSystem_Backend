package com.pavti.bal;

import com.pavti.common.ApiException;
import com.pavti.dal.BusinessDal;
import com.pavti.dal.PlanDal;
import com.pavti.dal.TableDal;
import com.pavti.dataobject.platform.Business;
import com.pavti.dataobject.platform.Plan;
import com.pavti.dataobject.table.TableCard;
import com.pavti.dataobject.table.TableRow;
import com.pavti.filter.CurrentUser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TableBal {

    private final TableDal tableDal;
    private final BusinessDal businessDal;
    private final PlanDal planDal;
    private final AuditBal audit;

    public TableBal(TableDal tableDal, BusinessDal businessDal, PlanDal planDal, AuditBal audit) {
        this.tableDal = tableDal;
        this.businessDal = businessDal;
        this.planDal = planDal;
        this.audit = audit;
    }

    public List<TableCard> getFloor(CurrentUser user) {
        return tableDal.getFloor(user.requireBusinessId());
    }

    /** Adds the next numbered table ("Table 9"); the plan may limit how many. */
    public long add(CurrentUser user) {
        long bid = user.requireBusinessId();
        Business b = businessDal.getById(bid).orElseThrow(() -> ApiException.notFound("Business not found."));
        Plan plan = planDal.getById(b.planId()).orElse(null);
        if (plan != null && plan.maxTables() != null && tableDal.count(bid) >= plan.maxTables()) {
            throw ApiException.badRequest("The " + plan.planName() + " plan allows " + plan.maxTables() + " tables.");
        }
        long id = tableDal.add(bid, null, user.userId());
        audit.log(user, "TABLE_CREATE", "dining_tables", id, null, null);
        return id;
    }

    public void remove(CurrentUser user, long tableId) {
        long bid = user.requireBusinessId();
        TableRow t = tableDal.getById(tableId, bid).orElseThrow(() -> ApiException.notFound("Table not found."));
        tableDal.remove(tableId, bid, user.userId());
        audit.log(user, "TABLE_REMOVE", "dining_tables", tableId, t.tableName(), null);
    }
}
