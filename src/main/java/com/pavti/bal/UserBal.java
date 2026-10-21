package com.pavti.bal;

import static com.pavti.common.TextHelper.clean;

import com.pavti.common.ApiException;
import com.pavti.common.PasswordHelper;
import com.pavti.common.TextHelper;
import com.pavti.dal.AuthDal;
import com.pavti.dal.BusinessDal;
import com.pavti.dal.PlanDal;
import com.pavti.dal.SessionDal;
import com.pavti.dal.UserDal;
import com.pavti.dataobject.platform.Business;
import com.pavti.dataobject.platform.Plan;
import com.pavti.dataobject.user.User;
import com.pavti.dataobject.user.UserBrief;
import com.pavti.dataobject.user.UserRow;
import com.pavti.dataobject.user.UserSaveRequest;
import com.pavti.dataobject.user.UserStatusRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.PageAction;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team (owner) and all users (platform admin).
 * Owners can add OWNER/STAFF to their own business only; the plan limits how many people are active.
 */
@Service
public class UserBal {

    private final UserDal userDal;
    private final AuthDal authDal;
    private final BusinessDal businessDal;
    private final PlanDal planDal;
    private final SessionDal sessionDal;
    private final RightsBal rights;
    private final AuditBal audit;
    private final PasswordHelper passwords;

    public UserBal(UserDal userDal, AuthDal authDal, BusinessDal businessDal, PlanDal planDal, SessionDal sessionDal,
                   RightsBal rights, AuditBal audit, PasswordHelper passwords) {
        this.userDal = userDal;
        this.authDal = authDal;
        this.businessDal = businessDal;
        this.planDal = planDal;
        this.sessionDal = sessionDal;
        this.rights = rights;
        this.audit = audit;
        this.passwords = passwords;
    }

    public List<User> getAll(CurrentUser user) {
        List<UserRow> rows;
        if (rights.canPage(user, "ALL_USERS", PageAction.VIEW)) {
            rows = userDal.getAll(null);
        } else {
            rights.requirePage(user, "TEAM", PageAction.VIEW);
            rows = userDal.getAll(user.requireBusinessId());
        }
        return rows.stream().map(UserBal::toUser).toList();
    }

    private static User toUser(UserRow r) {
        List<String> perms = "STAFF".equals(r.roleCode())
                ? Arrays.stream(clean(r.permissionCodes()).split(",")).filter(s -> !s.isBlank()).toList()
                : RightsBal.ALL_PERMISSIONS;
        return new User(r.userId(), r.businessId(), r.businessName(), r.roleCode(), r.fullName(), r.mobileNo(), r.email(),
                r.preferredLanguage(), perms, r.isActive(), r.lastLoginDate());
    }

    @Transactional
    public void save(CurrentUser user, UserSaveRequest req) {
        boolean isNew = req.userId() == null || req.userId() == 0;
        PageAction action = isNew ? PageAction.ADD : PageAction.EDIT;
        boolean platform = rights.canPage(user, "ALL_USERS", action);
        if (!platform) rights.requirePage(user, "TEAM", action);

        String name = clean(req.fullName());
        String mobile = TextHelper.mobile(req.mobileNo());
        String email = clean(req.email()).toLowerCase();
        String role = clean(req.roleCode());
        if (name.isEmpty()) throw ApiException.badRequest("Name is required.");
        if (name.length() > 100) throw ApiException.badRequest("Name is too long.");
        if (!TextHelper.isMobile(mobile)) throw ApiException.badRequest("Mobile number is not valid.");
        if (!email.isEmpty() && !TextHelper.isEmail(email)) throw ApiException.badRequest("Email is not valid.");
        List<String> roles = platform ? List.of("SUPER_ADMIN", "OWNER", "STAFF") : List.of("OWNER", "STAFF");
        if (!roles.contains(role)) throw ApiException.badRequest("Choose a role.");

        Long businessId = platform ? ("SUPER_ADMIN".equals(role) ? null : req.businessId()) : Long.valueOf(user.requireBusinessId());
        if (businessId != null && businessDal.getById(businessId).isEmpty()) throw ApiException.badRequest("Choose a business.");
        if (!"SUPER_ADMIN".equals(role) && businessId == null) throw ApiException.badRequest("Choose a business.");
        if (authDal.mobileExists(mobile, isNew ? null : req.userId())) throw ApiException.conflict("This mobile number is already used.");

        List<String> given = req.permissions() == null ? List.of() : req.permissions();
        List<String> permissions = "STAFF".equals(role) ? RightsBal.ALL_PERMISSIONS.stream().filter(given::contains).toList() : List.of();
        if ("STAFF".equals(role) && !permissions.contains("ORDERS_TAKE") && !permissions.contains("PAYMENTS_TAKE")) {
            throw ApiException.badRequest("Staff need at least \"Take orders\" or \"Take payments\".");
        }

        String password = clean(req.password());
        String hash = null;
        if (isNew) {
            if (password.length() < 6) throw ApiException.badRequest("Password must be at least 6 characters.");
            hash = passwords.hash(password);
            if (businessId != null) seatCheck(businessId);
        } else {
            UserBrief row = userDal.getById(req.userId()).orElseThrow(() -> ApiException.notFound("Person not found."));
            if (!platform && !Objects.equals(row.businessId(), user.businessId())) throw ApiException.notFound("Person not found.");
            if (row.userId() == user.userId() && !row.roleCode().equals(role)) throw ApiException.badRequest("You cannot change your own role.");
            if (!password.isEmpty()) {
                if (password.length() < 6) throw ApiException.badRequest("Password must be at least 6 characters.");
                hash = passwords.hash(password);
            }
            if (row.isActive() && businessId != null && !businessId.equals(row.businessId())) seatCheck(businessId);
        }

        long id = userDal.save(isNew ? null : req.userId(), businessId, role, name, mobile, email, hash, user.userId());
        if (businessId != null) userDal.setPermissions(id, businessId, permissions, user.userId());
        // a new password from the owner signs that person out everywhere
        if (!isNew && hash != null && id != user.userId()) sessionDal.closeAll(id, user.userId());
        audit.log(businessId, user.userId(), isNew ? "USER_CREATE" : "USER_UPDATE", "users", id, name + " (" + role + ")", null);
    }

    public void setStatus(CurrentUser user, UserStatusRequest req) {
        boolean platform = rights.canPage(user, "ALL_USERS", PageAction.EDIT);
        if (!platform) rights.requirePage(user, "TEAM", PageAction.EDIT);
        UserBrief row = userDal.getById(req.userId()).orElseThrow(() -> ApiException.notFound("Person not found."));
        if (!platform && !Objects.equals(row.businessId(), user.businessId())) throw ApiException.notFound("Person not found.");
        if (row.userId() == user.userId()) throw ApiException.badRequest("You cannot turn off your own login.");
        if (req.isActive() && !row.isActive() && row.businessId() != null) seatCheck(row.businessId());
        userDal.setStatus(row.userId(), req.isActive(), user.userId());
        if (!req.isActive()) sessionDal.closeAll(row.userId(), user.userId());
        audit.log(row.businessId(), user.userId(), req.isActive() ? "USER_ACTIVATE" : "USER_DEACTIVATE", "users", row.userId(),
                row.fullName(), null);
    }

    /** The plan decides how many people can be active in one business. */
    private void seatCheck(long businessId) {
        Business b = businessDal.getById(businessId).orElseThrow(() -> ApiException.badRequest("Choose a business."));
        Plan plan = planDal.getById(b.planId()).orElse(null);
        if (plan != null && userDal.countActive(businessId) >= plan.maxUsers()) {
            throw ApiException.badRequest("The " + plan.planName() + " plan allows " + plan.maxUsers()
                    + " people. Turn someone off or change the plan.");
        }
    }
}
