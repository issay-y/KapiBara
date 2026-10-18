    package com.cafe.kapibara.kapibara.controller.main.admin;

    public enum AdminPage {

        HOME("Café administration",
                "/com/cafe/kapibara/kapibara/controller/main/admin/home/HomeView.fxml"),
        TRANSACTIONS("Transactions",
                "/com/cafe/kapibara/kapibara/controller/main/admin/transaction/TransactionView.fxml"),
        TRANSACTION_DETAIL("Transactions / Details",
                "/com/cafe/kapibara/kapibara/controller/main/admin/transaction/TransactionDetailView.fxml",
                TRANSACTIONS),
        SALES("Sales overview",
                "/com/cafe/kapibara/kapibara/controller/main/admin/sale/SaleView.fxml"),
        INVENTORY("Inventory",
                "/com/cafe/kapibara/kapibara/controller/main/admin/inventory/InventoryView.fxml"),
        LOW_STOCK("Inventory / Low stock priorities",
                "/com/cafe/kapibara/kapibara/controller/main/admin/inventory/LowStockView.fxml",
                INVENTORY),
        INVENTORY_ADD("Inventory / Add item",
                "/com/cafe/kapibara/kapibara/controller/main/admin/inventory/InventoryFormView.fxml",
                INVENTORY),
        INVENTORY_EDIT("Inventory / Edit item",
                "/com/cafe/kapibara/kapibara/controller/main/admin/inventory/InventoryFormView.fxml",
                INVENTORY),
        ACCOUNTS("Team accounts",
                "/com/cafe/kapibara/kapibara/controller/main/admin/account/AccountView.fxml"),
        ACCOUNT_ADD("Team accounts / Add account",
                "/com/cafe/kapibara/kapibara/controller/main/admin/account/AccountFormView.fxml",
                ACCOUNTS),
        ACCOUNT_EDIT("Team accounts / Edit account",
                "/com/cafe/kapibara/kapibara/controller/main/admin/account/AccountFormView.fxml",
                ACCOUNTS),
        SETTINGS("Settings",
                "/com/cafe/kapibara/kapibara/controller/main/admin/settings/SettingsView.fxml");

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

        public AdminPage getNavGroup() {
            return navGroup == null ? this : navGroup;
        }
    }