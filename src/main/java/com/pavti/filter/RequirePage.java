package com.pavti.filter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Needs a page right, e.g. @RequirePage(page = "TABLES", action = PageAction.ADD).
 * Checked against role_page_rights + the staff tick the page needs (sp_menu_binding).
 * Also checks the token, so @ValidateToken is not needed next to it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePage {
    String page();

    PageAction action() default PageAction.VIEW;
}
