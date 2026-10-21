package com.pavti.bal;

import static com.pavti.common.TextHelper.clean;

import com.pavti.common.ApiException;
import com.pavti.common.TextHelper;
import com.pavti.dal.MenuItemDal;
import com.pavti.dataobject.menu.CategorySaveRequest;
import com.pavti.dataobject.menu.ItemAvailabilityRequest;
import com.pavti.dataobject.menu.ItemSaveResult;
import com.pavti.dataobject.menu.MenuCategory;
import com.pavti.dataobject.menu.MenuItem;
import com.pavti.dataobject.menu.MenuItemSaveRequest;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.PageAction;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/** Menu groups and dishes. Everyone in the business can read the menu (needed to take orders). */
@Service
public class MenuItemBal {

    private final MenuItemDal dal;
    private final RightsBal rights;
    private final AuditBal audit;

    public MenuItemBal(MenuItemDal dal, RightsBal rights, AuditBal audit) {
        this.dal = dal;
        this.rights = rights;
        this.audit = audit;
    }

    public List<MenuCategory> getCategories(CurrentUser user) {
        return dal.getCategories(user.requireBusinessId());
    }

    public void saveCategory(CurrentUser user, CategorySaveRequest req) {
        long bid = user.requireBusinessId();
        boolean isNew = req.categoryId() == null || req.categoryId() == 0;
        rights.requirePage(user, "MENU", isNew ? PageAction.ADD : PageAction.EDIT);
        String name = clean(req.categoryName());
        if (name.isEmpty()) throw ApiException.badRequest("Group name is required.");
        if (name.length() > 100) throw ApiException.badRequest("Group name is too long.");
        if (!isNew) category(bid, req.categoryId());
        dal.saveCategory(isNew ? null : req.categoryId(), bid, name, user.userId());
    }

    public void deleteCategory(CurrentUser user, long categoryId) {
        long bid = user.requireBusinessId();
        MenuCategory c = category(bid, categoryId);
        dal.deleteCategory(categoryId, bid, user.userId());
        audit.log(user, "CATEGORY_DELETE", "menu_categories", categoryId, c.categoryName(), null);
    }

    private MenuCategory category(long bid, long categoryId) {
        return dal.getCategories(bid).stream().filter(c -> c.categoryId() == categoryId).findFirst()
                .orElseThrow(() -> ApiException.notFound("Group not found."));
    }

    public List<MenuItem> getAll(CurrentUser user) {
        return dal.getAll(user.requireBusinessId());
    }

    public void save(CurrentUser user, MenuItemSaveRequest req) {
        long bid = user.requireBusinessId();
        boolean isNew = req.itemId() == null || req.itemId() == 0;
        rights.requirePage(user, "MENU", isNew ? PageAction.ADD : PageAction.EDIT);

        String name = clean(req.itemName());
        if (name.isEmpty()) throw ApiException.badRequest("Dish name is required.");
        if (name.length() > 150) throw ApiException.badRequest("Dish name is too long.");
        if (req.price() == null) throw ApiException.badRequest("Enter the price.");
        BigDecimal price = TextHelper.money(req.price());
        if (price.signum() < 0) throw ApiException.badRequest("Price cannot be negative.");
        BigDecimal taxRate = req.taxRate() == null ? BigDecimal.ZERO : req.taxRate();
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.valueOf(28)) > 0) throw ApiException.badRequest("Tax must be between 0 and 28%.");
        String imageUrl = clean(req.imageUrl());
        if (!imageUrl.isEmpty() && !imageUrl.startsWith("https://")) throw ApiException.badRequest("Photo link must start with https://");
        if (imageUrl.length() > 500) throw ApiException.badRequest("Photo link is too long.");
        String unit = clean(req.unitName()).isEmpty() ? "plate" : clean(req.unitName());
        Long categoryId = req.categoryId() == null || req.categoryId() == 0 ? null : req.categoryId();

        MenuItem old = isNew ? null : dal.getById(req.itemId(), bid).orElseThrow(() -> ApiException.notFound("Dish not found."));
        ItemSaveResult r = dal.save(isNew ? null : req.itemId(), bid, categoryId, clean(req.itemCode()), name, imageUrl, unit, price,
                taxRate, Boolean.TRUE.equals(req.isVeg()), !Boolean.FALSE.equals(req.isAvailable()), user.userId());
        if (isNew) audit.log(user, "ITEM_CREATE", "menu_items", r.itemId(), name, null);
        else if (r.priceChanged()) audit.log(user, "PRICE_CHANGE", "menu_items", r.itemId(), name + ": " + old.price() + " -> " + price, null);
        else audit.log(user, "ITEM_UPDATE", "menu_items", r.itemId(), name, null);
    }

    public void setAvailability(CurrentUser user, ItemAvailabilityRequest req) {
        long bid = user.requireBusinessId();
        MenuItem it = dal.getById(req.itemId(), bid).orElseThrow(() -> ApiException.notFound("Dish not found."));
        dal.setAvailability(it.itemId(), bid, req.isAvailable(), user.userId());
        audit.log(user, req.isAvailable() ? "ITEM_AVAILABLE" : "ITEM_SOLD_OUT", "menu_items", it.itemId(), it.itemName(), null);
    }
}
