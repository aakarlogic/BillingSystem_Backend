package com.pavti.controller;

import com.pavti.bal.PaymentBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.order.Order;
import com.pavti.dataobject.payment.Payment;
import com.pavti.dataobject.payment.PaymentSaveRequest;
import com.pavti.dataobject.payment.RefundRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.ValidateEncryptedRequest;
import com.pavti.filter.RequirePage;
import com.pavti.filter.RequirePermission;
import com.pavti.filter.RequireRole;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Money received against bills (cash / UPI scanner, outside the app). */
@RestController
@RequestMapping("/api/Payment")
@ValidateEncryptedRequest
public class PaymentController extends BaseApiController {

    private final PaymentBal bal;

    public PaymentController(PaymentBal bal) {
        this.bal = bal;
    }

    @RequirePage(page = "PAYMENTS")
    @PostMapping("/GetAll")
    public ApiResponse<List<Payment>> getAll(CurrentUser user) {
        return ok(bal.getAll(user));
    }

    @RequirePermission("PAYMENTS_TAKE")
    @PostMapping("/Save")
    public ApiResponse<Order> save(@Valid @RequestBody PaymentSaveRequest req, CurrentUser user) {
        return ok(bal.save(user, req), "Payment saved");
    }

    @RequireRole("OWNER")
    @PostMapping("/Refund")
    public ApiResponse<Boolean> refund(@Valid @RequestBody RefundRequest req, CurrentUser user) {
        bal.refund(user, req);
        return ok(true, "Refund saved");
    }
}
