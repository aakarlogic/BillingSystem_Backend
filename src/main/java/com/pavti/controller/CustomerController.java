package com.pavti.controller;

import com.pavti.bal.CustomerBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.common.IdRequest;
import com.pavti.dataobject.customer.CustomerListItem;
import com.pavti.dataobject.customer.CustomerSaveRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.ValidateEncryptedRequest;
import com.pavti.filter.PageAction;
import com.pavti.filter.RequirePage;
import com.pavti.filter.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/Customer")
@ValidateEncryptedRequest
public class CustomerController extends BaseApiController {

    private final CustomerBal bal;

    public CustomerController(CustomerBal bal) {
        this.bal = bal;
    }

    @RequirePage(page = "CUSTOMERS")
    @PostMapping("/GetAll")
    public ApiResponse<List<CustomerListItem>> getAll(CurrentUser user) {
        return ok(bal.getAll(user));
    }

    @RequirePermission("ORDERS_TAKE")
    @PostMapping("/Save")
    public ApiResponse<Long> save(@RequestBody CustomerSaveRequest req, CurrentUser user) {
        return ok(bal.save(user, req), "Saved");
    }

    @RequirePage(page = "CUSTOMERS", action = PageAction.DELETE)
    @PostMapping("/Delete")
    public ApiResponse<Boolean> delete(@Valid @RequestBody IdRequest req, CurrentUser user) {
        bal.delete(user, req.id());
        return ok(true, "Removed");
    }
}
