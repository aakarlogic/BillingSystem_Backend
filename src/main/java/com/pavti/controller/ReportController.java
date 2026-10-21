package com.pavti.controller;

import com.pavti.bal.ReportBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.report.DashboardSummary;
import com.pavti.dataobject.report.ReportRequest;
import com.pavti.dataobject.report.SalesReport;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.ValidateEncryptedRequest;
import com.pavti.filter.RequirePage;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/Report")
@ValidateEncryptedRequest
public class ReportController extends BaseApiController {

    private final ReportBal bal;

    public ReportController(ReportBal bal) {
        this.bal = bal;
    }

    @RequirePage(page = "HOME")
    @PostMapping("/GetDashboard")
    public ApiResponse<DashboardSummary> getDashboard(CurrentUser user) {
        return ok(bal.dashboard(user));
    }

    @RequirePage(page = "REPORTS")
    @PostMapping("/GetSales")
    public ApiResponse<SalesReport> getSales(@Valid @RequestBody ReportRequest req, CurrentUser user) {
        return ok(bal.sales(user, req));
    }
}
