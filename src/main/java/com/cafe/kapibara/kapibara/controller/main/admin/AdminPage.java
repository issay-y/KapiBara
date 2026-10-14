package com.cafe.kapibara.kapibara.controller.main.admin;

/** Every page that can be shown inside the admin shell (sidebar + top bar). */
public enum AdminPage {

    HOME("Café administration",
            "/com/cafe/kapibara/kapibara/controller/main/admin/home/HomeView.fxml"),
    TRANSACTIONS("Transactions",
            "/com/cafe/kapibara/kapibara/controller/main/admin/transaction/TransactionView.fxml"),
    SALES("Sales overview",
            "/com/cafe/kapibara/kapibara/controller/main/admin/sale/SaleView.fxml"),
    INVENTORY("Inventory",
            "/com/cafe/kapibara/kapibara/controller/main/admin/inventory/InventoryView.fxml"),
    // A sub-page of Inventory: the "Inventory" sidebar button stays highlighted.
    LOW_STOCK("Inventory / Low stock priorities",
            "/com/cafe/kapibara/kapibara/controller/main/admin/inventory/LowStockView.fxml",
            INVENTORY),
    // Add and Edit share one form page; the form decides which mode it is in from the payload.
    INVENTORY_ADD("Inventory / Add item",
            "/com/cafe/kapibara/kapibara/controller/main/admin/inventory/InventoryFormView.fxml",
            INVENTORY),
    INVENTORY_EDIT("Inventory / Edit item",
            "/com/cafe/kapibara/kapibara/controller/main/admin/inventory/InventoryFormView.fxml",
            INVENTORY),
    ACCOUNTS("Team accounts",
            "/com/cafe/kapibara/kapibara/controller/main/admin/account/AccountView.fxml"),
    // Add and Edit share one form page; the form decides which mode it is in from the payload.
    ACCOUNT_ADD("Team accounts / Add account",
            "/com/cafe/kapibara/kapibara/controller/main/admin/account/AccountFormView.fxml",
            ACCOUNTS),
    ACCOUNT_EDIT("Team accounts / Edit account",
            "/com/cafe/kapibara/kapibara/controller/main/admin/account/AccountFormView.fxml",
            ACCOUNTS),
    // Shared with the cashier shell: the same FXML is loaded there too.
    PROFILE("Profile",
            "/com/cafe/kapibara/kapibara/controller/main/profile/ProfileView.fxml");

    private final String breadcrumb;
    private final String fxml;
    private final AdminPage navGroup;

    AdminPage(String breadcrumb, String fxml) {
        this(breadcrumb, fxml, null);
    }

    AdminPage(String breadcrumb, String fxml, AdminPage navGroup) {
        this.breadcrumb = breadcrumb;
        this.fxml = fxml;
        this.navGroup = navGroup;
    }

    public String getBreadcrumb() {
        return breadcrumb;
    }

    public String getFxml() {
        return fxml;
    }

    /** The page whose sidebar button should be highlighted while this page is open. */
    public AdminPage getNavGroup() {
        return navGroup == null ? this : navGroup;
    }
}