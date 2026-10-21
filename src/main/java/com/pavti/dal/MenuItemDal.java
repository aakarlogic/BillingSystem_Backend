package com.pavti.dal;

import com.pavti.dataobject.menu.ItemSaveResult;
import com.pavti.dataobject.menu.MenuCategory;
import com.pavti.dataobject.menu.MenuItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Menu groups and dishes. */
@Repository
public class MenuItemDal {

    private final DataClass db;

    public MenuItemDal(DataClass db) {
        this.db = db;
    }

    public List<MenuCategory> getCategories(long businessId) {
        return db.getList("sp_category_get_all", MenuCategory.class, businessId);
    }

    public long saveCategory(Long categoryId, long businessId, String name, long by) {
        return db.getLong("sp_category_save", categoryId, businessId, name, by);
    }

    public void deleteCategory(long categoryId, long businessId, long by) {
        db.execute("sp_category_delete", categoryId, businessId, by);
    }

    public List<MenuItem> getAll(long businessId) {
        return db.getList("sp_item_get_all", MenuItem.class, businessId);
    }

    public Optional<MenuItem> getById(long itemId, long businessId) {
        return db.getOne("sp_item_get_by_id", MenuItem.class, itemId, businessId);
    }

    public ItemSaveResult save(Long itemId, long businessId, Long categoryId, String itemCode, String itemName, String imageUrl,
                               String unitName, BigDecimal price, BigDecimal taxRate, boolean isVeg, boolean isAvailable, long by) {
        return db.getOne("sp_item_save", ItemSaveResult.class, itemId, businessId, categoryId, itemCode, itemName, imageUrl, unitName,
                price, taxRate, isVeg ? 1 : 0, isAvailable ? 1 : 0, by).orElseThrow();
    }

    public boolean setAvailability(long itemId, long businessId, boolean isAvailable, long by) {
        return db.getLong("sp_item_set_availability", itemId, businessId, isAvailable ? 1 : 0, by) > 0;
    }
}
