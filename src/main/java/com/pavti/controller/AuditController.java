package com.pavti.controller;

import com.pavti.bal.AuditBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.audit.AuditLog;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.ValidateEncryptedRequest;
import com.pavti.filter.RequirePage;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** History: owner sees their business, platform admin sees everything. */
@RestController
@RequestMapping("/api/Audit")
@ValidateEncryptedRequest
public class AuditController extends BaseApiController {

    private final AuditBal bal;

    public AuditController(AuditBal bal) {
        this.bal = bal;
    }

    @RequirePage(page = "AUDIT_LOG")
    @PostMapping("/GetAll")
    public ApiResponse<List<AuditLog>> getAll(CurrentUser user) {
        return ok(bal.getAll(user));
    }
}
