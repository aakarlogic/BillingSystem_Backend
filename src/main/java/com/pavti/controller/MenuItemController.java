package com.pavti.controller;

import com.pavti.bal.MenuItemBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.common.IdRequest;
import com.pavti.dataobject.menu.CategorySaveRequest;
import com.pavti.dataobject.menu.ItemAvailabilityRequest;
import com.pavti.dataobject.menu.MenuCategory;
import com.pavti.dataobject.menu.MenuItem;
import com.pavti.dataobject.menu.MenuItemSaveRequest;
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

/** Menu groups and dishes. Reading is open to the whole team (needed to take orders). */
@RestController
@RequestMapping("/api/MenuItem")
@ValidateEncryptedRequest
public class MenuItemController extends BaseApiController {

    private final MenuItemBal bal;

    public MenuItemController(MenuItemBal bal) {
        this.bal = bal;
    }

    @ValidateToken
    @PostMapping("/GetCategories")
    public ApiResponse<List<MenuCategory>> getCategories(CurrentUser user) {
        return ok(bal.getCategories(user));
    }

    @ValidateToken
    @PostMapping("/SaveCategory")
    public ApiResponse<Boolean> saveCategory(@RequestBody CategorySaveRequest req, CurrentUser user) {
        bal.saveCategory(user, req);
        return ok(true, "Saved");
    }

    @RequirePage(page = "MENU", action = PageAction.DELETE)
    @PostMapping("/DeleteCategory")
    public ApiResponse<Boolean> deleteCategory(@Valid @RequestBody IdRequest req, CurrentUser user) {
        bal.deleteCategory(user, req.id());
        return ok(true, "Removed");
    }

    @ValidateToken
    @PostMapping("/GetAll")
    public ApiResponse<List<MenuItem>> getAll(CurrentUser user) {
        return ok(bal.getAll(user));
    }

    @ValidateToken
    @PostMapping("/Save")
    public ApiResponse<Boolean> save(@RequestBody MenuItemSaveRequest req, CurrentUser user) {
        bal.save(user, req);
        return ok(true, "Saved");
    }

    @RequirePage(page = "MENU", action = PageAction.EDIT)
    @PostMapping("/SetAvailability")
    public ApiResponse<Boolean> setAvailability(@Valid @RequestBody ItemAvailabilityRequest req, CurrentUser user) {
        bal.setAvailability(user, req);
        return ok(true, "Saved");
    }
}
