package com.pavti.bal;

import static com.pavti.common.TextHelper.clean;

import com.pavti.common.ApiException;
import com.pavti.common.TextHelper;
import com.pavti.dal.CustomerDal;
import com.pavti.dataobject.customer.CustomerListItem;
import com.pavti.dataobject.customer.CustomerRow;
import com.pavti.dataobject.customer.CustomerSaveRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.PageAction;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerBal {

    private final CustomerDal dal;
    private final RightsBal rights;
    private final AuditBal audit;

    public CustomerBal(CustomerDal dal, RightsBal rights, AuditBal audit) {
        this.dal = dal;
        this.rights = rights;
        this.audit = audit;
    }

    public List<CustomerListItem> getAll(CurrentUser user) {
        return dal.getAll(user.requireBusinessId());
    }

    public long save(CurrentUser user, CustomerSaveRequest req) {
        long bid = user.requireBusinessId();
        boolean isNew = req.customerId() == null || req.customerId() == 0;
        rights.requirePage(user, "CUSTOMERS", isNew ? PageAction.ADD : PageAction.EDIT);
        String name = clean(req.customerName());
        if (name.isEmpty()) throw ApiException.badRequest("Customer name is required.");
        if (name.length() > 150) throw ApiException.badRequest("Customer name is too long.");
        String email = clean(req.email()).toLowerCase();
        if (!email.isEmpty() && !TextHelper.isEmail(email)) throw ApiException.badRequest("Email is not valid.");
        String mobile = TextHelper.mobile(req.mobileNo());
        if (!mobile.isEmpty() && !TextHelper.isMobile(mobile)) throw ApiException.badRequest("Mobile number is not valid.");
        if (!isNew) get(bid, req.customerId());
        return dal.save(isNew ? null : req.customerId(), bid, name, mobile, email, clean(req.addressLine()),
                clean(req.gstin()).toUpperCase(), user.userId());
    }

    public void delete(CurrentUser user, long customerId) {
        long bid = user.requireBusinessId();
        CustomerRow c = get(bid, customerId);
        dal.delete(customerId, bid, user.userId());
        audit.log(user, "CUSTOMER_DELETE", "customers", customerId, c.customerName(), null);
    }

    private CustomerRow get(long bid, long customerId) {
        return dal.getById(customerId, bid).orElseThrow(() -> ApiException.notFound("Customer not found."));
    }
}
