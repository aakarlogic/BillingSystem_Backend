package com.pavti.bal;

import com.pavti.common.ApiException;
import com.pavti.dal.AuthDal;
import com.pavti.dal.MenuDal;
import com.pavti.dataobject.auth.MenuBindingItem;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.PageAction;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Authorization: role page rights (role_page_rights + pages.required_permission_code)
 * and staff ticks (user_permissions). Same rules as database/README.md section 4.
 */
@Service
public class RightsBal {

    /** Every staff tick, in screen order. Owners and the platform admin have all of them. */
    public static final List<String> ALL_PERMISSIONS =
            List.of("ORDERS_TAKE", "PAYMENTS_TAKE", "DISCOUNTS_GIVE", "BILLS_CANCEL", "SALES_VIEW");

    private final MenuDal menuDal;
    private final AuthDal authDal;

    public RightsBal(MenuDal menuDal, AuthDal authDal) {
        this.menuDal = menuDal;
        this.authDal = authDal;
    }

    public List<MenuBindingItem> menu(CurrentUser user) {
        return menuDal.getMenuBinding(user.userId());
    }

    public boolean canPage(CurrentUser user, String pageCode, PageAction action) {
        return menu(user).stream()
                .filter(m -> m.pageCode().equals(pageCode))
                .anyMatch(m -> switch (action) {
                    case VIEW -> m.canView();
                    case ADD -> m.canAdd();
                    case EDIT -> m.canEdit();
                    case DELETE -> m.canDelete();
                    case EXPORT -> m.canExport();
                });
    }

    public void requirePage(CurrentUser user, String pageCode, PageAction action) {
        if (!canPage(user, pageCode, action)) throw ApiException.forbidden("You do not have permission to do this.");
    }

    /** Owners and the platform admin have every permission; staff only what is ticked. */
    public boolean hasPermission(CurrentUser user, String permissionCode) {
        return !user.isStaff() || authDal.getUserPermissions(user.userId()).contains(permissionCode);
    }

    public void requirePermission(CurrentUser user, String permissionCode) {
        if (!hasPermission(user, permissionCode)) throw ApiException.forbidden("You do not have permission to do this.");
    }
}
