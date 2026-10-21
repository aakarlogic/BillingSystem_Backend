package com.pavti.dal;

import com.pavti.dataobject.auth.MenuBindingItem;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Page rights for the side menu (sp_menu_binding). */
@Repository
public class MenuDal {

    private final DataClass db;

    public MenuDal(DataClass db) {
        this.db = db;
    }

    public List<MenuBindingItem> getMenuBinding(long userId) {
        return db.getList("sp_menu_binding", MenuBindingItem.class, userId);
    }
}
