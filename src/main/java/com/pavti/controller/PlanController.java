package com.pavti.controller;

import com.pavti.bal.PlanBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.common.IdRequest;
import com.pavti.dataobject.platform.Plan;
import com.pavti.dataobject.platform.PlanSaveRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.ValidateEncryptedRequest;
import com.pavti.filter.PageAction;
import com.pavti.filter.RequirePage;
import com.pavti.filter.ValidateToken;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/Plan")
@ValidateEncryptedRequest
public class PlanController extends BaseApiController {

    private final PlanBal bal;

    public PlanController(PlanBal bal) {
        this.bal = bal;
    }

    /** Public: the sign-up page shows the plans. */
    @PostMapping("/GetAll")
    public ApiResponse<List<Plan>> getAll() {
        return ok(bal.getAll());
    }

    @ValidateToken
    @PostMapping("/Save")
    public ApiResponse<Boolean> save(@RequestBody PlanSaveRequest req, CurrentUser user) {
        bal.save(user, req);
        return ok(true, "Saved");
    }

    @RequirePage(page = "PLANS", action = PageAction.DELETE)
    @PostMapping("/Delete")
    public ApiResponse<Boolean> delete(@Valid @RequestBody IdRequest req, CurrentUser user) {
        bal.delete(user, req.id());
        return ok(true, "Removed");
    }
}
