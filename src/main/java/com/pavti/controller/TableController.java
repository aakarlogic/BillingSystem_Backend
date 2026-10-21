package com.pavti.controller;

import com.pavti.bal.TableBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.common.IdRequest;
import com.pavti.dataobject.table.TableCard;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.ValidateEncryptedRequest;
import com.pavti.filter.PageAction;
import com.pavti.filter.RequirePage;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/Table")
@ValidateEncryptedRequest
public class TableController extends BaseApiController {

    private final TableBal bal;

    public TableController(TableBal bal) {
        this.bal = bal;
    }

    @RequirePage(page = "TABLES")
    @PostMapping("/GetFloor")
    public ApiResponse<List<TableCard>> getFloor(CurrentUser user) {
        return ok(bal.getFloor(user));
    }

    @RequirePage(page = "TABLES", action = PageAction.ADD)
    @PostMapping("/Add")
    public ApiResponse<Long> add(CurrentUser user) {
        return ok(bal.add(user), "Table added");
    }

    @RequirePage(page = "TABLES", action = PageAction.DELETE)
    @PostMapping("/Remove")
    public ApiResponse<Boolean> remove(@Valid @RequestBody IdRequest req, CurrentUser user) {
        bal.remove(user, req.id());
        return ok(true, "Removed");
    }
}
