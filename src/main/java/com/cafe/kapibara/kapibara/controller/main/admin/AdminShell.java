package com.cafe.kapibara.kapibara.controller.main.admin;

/**
 * What a page controller can ask of the admin shell (sidebar + top bar).
 * A page controller implements {@link Aware} to receive the shell after its FXML is loaded.
 */
public interface AdminShell {

    void navigate(AdminPage page);

    /** Opens a page and hands it a value (for example the id of the account to edit). */
    void navigate(AdminPage page, Object payload);

    /** The value passed to the page that is being opened, or null. Read it inside attach(). */
    Object getPagePayload();

    String getCurrentUserName();

    String getCurrentUserRole();

    /** Signs the current user out (same action as the sidebar / top bar "Logout"). */
    void logout();

    interface Aware {
        void attach(AdminShell shell);
    }
}