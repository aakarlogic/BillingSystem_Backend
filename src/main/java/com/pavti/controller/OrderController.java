package com.pavti.controller;

import com.pavti.bal.OrderBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.common.IdRequest;
import com.pavti.dataobject.common.ReasonRequest;
import com.pavti.dataobject.order.Order;
import com.pavti.dataobject.order.OrderDetail;
import com.pavti.dataobject.order.OrderFilter;
import com.pavti.dataobject.order.OrderListItem;
import com.pavti.dataobject.order.OrderSaveRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.ValidateEncryptedRequest;
import com.pavti.filter.RequirePage;
import com.pavti.filter.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Orders and bills. */
@RestController
@RequestMapping("/api/Order")
@ValidateEncryptedRequest
public class OrderController extends BaseApiController {

    private final OrderBal bal;

    public OrderController(OrderBal bal) {
        this.bal = bal;
    }

    @RequirePage(page = "BILLS")
    @PostMapping("/GetAll")
    public ApiResponse<List<OrderListItem>> getAll(@RequestBody(required = false) OrderFilter filter, CurrentUser user) {
        return ok(bal.getAll(user, filter));
    }

    @RequirePage(page = "BILLS")
    @PostMapping("/GetById")
    public ApiResponse<OrderDetail> getById(@Valid @RequestBody IdRequest req, CurrentUser user) {
        return ok(bal.getDetail(user, req.id()));
    }

    @RequirePermission("ORDERS_TAKE")
    @PostMapping("/Save")
    public ApiResponse<Order> save(@RequestBody OrderSaveRequest req, CurrentUser user) {
        return ok(bal.save(user, req), req.makeBill() ? "Bill made" : "Order saved");
    }

    @RequirePermission("ORDERS_TAKE")
    @PostMapping("/MakeBill")
    public ApiResponse<Order> makeBill(@Valid @RequestBody IdRequest req, CurrentUser user) {
        return ok(bal.makeBill(user, req.id()), "Bill made");
    }

    @RequirePermission("BILLS_CANCEL")
    @PostMapping("/Cancel")
    public ApiResponse<Boolean> cancel(@Valid @RequestBody ReasonRequest req, CurrentUser user) {
        bal.cancel(user, req);
        return ok(true, "Bill cancelled");
    }
}
