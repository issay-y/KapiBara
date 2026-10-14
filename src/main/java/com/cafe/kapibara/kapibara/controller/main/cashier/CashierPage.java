package com.cafe.kapibara.kapibara.controller.main.cashier;

public enum CashierPage {

    HOME("Home",
            "/com/cafe/kapibara/kapibara/controller/main/cashier/home/CashierHomeView.fxml"),
    NEW_ORDER("New order",
            "/com/cafe/kapibara/kapibara/controller/main/cashier/order/NewOrderView.fxml"),
    BROWSE_MENU("Browse the menu",
            "/com/cafe/kapibara/kapibara/controller/main/cashier/menu/BrowseMenuView.fxml",
            NEW_ORDER),
    CHECKOUT("Cash payment",
            "/com/cafe/kapibara/kapibara/controller/main/cashier/checkout/CheckoutView.fxml",
            NEW_ORDER),
    RECEIPT("Payment complete",
            "/com/cafe/kapibara/kapibara/controller/main/cashier/receipt/ReceiptView.fxml",
            NEW_ORDER),
    TRANSACTIONS("Transactions",
            "/com/cafe/kapibara/kapibara/controller/main/cashier/transactions/TransactionsView.fxml"),
    TRANSACTION_DETAIL("Transaction details",
            "/com/cafe/kapibara/kapibara/controller/main/cashier/transactions/TransactionDetailView.fxml",
            TRANSACTIONS),
        PROFILE("Profile",
            "/com/cafe/kapibara/kapibara/controller/main/profile/ProfileView.fxml");

    private final String breadcrumb;
    private final String fxml;
    private final CashierPage navGroup;

    CashierPage(String breadcrumb, String fxml) {
        this(breadcrumb, fxml, null);
    }

    CashierPage(String breadcrumb, String fxml, CashierPage navGroup) {
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

    public CashierPage getNavGroup() {
        return navGroup == null ? this : navGroup;
    }
}