package com.pavti.controller;

import com.pavti.bal.RightsBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.auth.MenuBindingData;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.ValidateEncryptedRequest;
import com.pavti.filter.ValidateToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Side menu + page rights for the signed-in person (Health 360: Role/GetPermissions + menu binding). */
@RestController
@RequestMapping("/api/Menu")
@ValidateEncryptedRequest
@ValidateToken
public class MenuController extends BaseApiController {

    private final RightsBal rights;

    public MenuController(RightsBal rights) {
        this.rights = rights;
    }

    @PostMapping("/GetMenuBinding")
    public ApiResponse<MenuBindingData> getMenuBinding(CurrentUser user) {
        return ok(new MenuBindingData(rights.menu(user)));
    }
}
