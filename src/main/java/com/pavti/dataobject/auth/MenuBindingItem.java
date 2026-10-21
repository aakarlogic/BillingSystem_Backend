package com.pavti.dataobject.auth;

/** One row of sp_menu_binding: a page this person may open, with its rights. */
public record MenuBindingItem(
        String pageCode,
        String routePath,
        String menuGroup,
        String iconName,
        boolean showInMenu,
        int sortOrder,
        boolean canView,
        boolean canAdd,
        boolean canEdit,
        boolean canDelete,
        boolean canExport) {}
