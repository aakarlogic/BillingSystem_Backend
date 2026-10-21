package com.pavti.controller;

import com.pavti.bal.BusinessBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.platform.Business;
import com.pavti.dataobject.platform.BusinessListItem;
import com.pavti.dataobject.platform.BusinessSaveRequest;
import com.pavti.dataobject.platform.BusinessStatusRequest;
import com.pavti.dataobject.platform.PlatformStats;
import com.pavti.dataobject.platform.SettingsSaveRequest;
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

/** Businesses (platform admin) and the owner's Settings. */
@RestController
@RequestMapping("/api/Business")
@ValidateEncryptedRequest
public class BusinessController extends BaseApiController {

    private final BusinessBal bal;

    public BusinessController(BusinessBal bal) {
        this.bal = bal;
    }

    @RequirePage(page = "BUSINESSES")
    @PostMapping("/GetAll")
    public ApiResponse<List<BusinessListItem>> getAll() {
        return ok(bal.getAll());
    }

    /** Add or edit: the right (ADD / EDIT) is checked in BusinessBal. */
    @ValidateToken
    @PostMapping("/Save")
    public ApiResponse<Long> save(@RequestBody BusinessSaveRequest req, CurrentUser user) {
        return ok(bal.save(user, req), "Saved");
    }

    @RequirePage(page = "BUSINESSES", action = PageAction.EDIT)
    @PostMapping("/SetStatus")
    public ApiResponse<Boolean> setStatus(@Valid @RequestBody BusinessStatusRequest req, CurrentUser user) {
        bal.setStatus(user, req);
        return ok(true, "Saved");
    }

    @RequirePage(page = "BUSINESSES")
    @PostMapping("/GetStats")
    public ApiResponse<PlatformStats> getStats() {
        return ok(bal.stats());
    }

    /** Everyone in the business can read these (bills print the name, GSTIN and footer). */
    @ValidateToken
    @PostMapping("/GetSettings")
    public ApiResponse<Business> getSettings(CurrentUser user) {
        return ok(bal.get(user.requireBusinessId()));
    }

    @RequirePage(page = "SETTINGS", action = PageAction.EDIT)
    @PostMapping("/SaveSettings")
    public ApiResponse<Business> saveSettings(@RequestBody SettingsSaveRequest req, CurrentUser user) {
        return ok(bal.saveSettings(user, req), "Saved");
    }
}
