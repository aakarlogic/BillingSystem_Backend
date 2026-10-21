package com.pavti.controller;

import com.pavti.bal.UserBal;
import com.pavti.common.ApiResponse;
import com.pavti.dataobject.user.User;
import com.pavti.dataobject.user.UserSaveRequest;
import com.pavti.dataobject.user.UserStatusRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.ValidateEncryptedRequest;
import com.pavti.filter.ValidateToken;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Team (owner: TEAM page) and all users (platform admin: ALL_USERS page); rights checked in UserBal. */
@RestController
@RequestMapping("/api/User")
@ValidateEncryptedRequest
@ValidateToken
public class UserController extends BaseApiController {

    private final UserBal bal;

    public UserController(UserBal bal) {
        this.bal = bal;
    }

    @PostMapping("/GetAll")
    public ApiResponse<List<User>> getAll(CurrentUser user) {
        return ok(bal.getAll(user));
    }

    @PostMapping("/Save")
    public ApiResponse<Boolean> save(@RequestBody UserSaveRequest req, CurrentUser user) {
        bal.save(user, req);
        return ok(true, "Saved");
    }

    @PostMapping("/SetStatus")
    public ApiResponse<Boolean> setStatus(@Valid @RequestBody UserStatusRequest req, CurrentUser user) {
        bal.setStatus(user, req);
        return ok(true, "Saved");
    }
}
