package com.cafe.kapibara.kapibara.model.account;
import java.util.Locale;

public enum AccountRole {
    ADMIN("Admin"),
    CASHIER("Cashier"),
    MANAGER("Manager");

    private final String displayName;

    AccountRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AccountRole fromString(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            return CASHIER;
        }
        try {
            return AccountRole.valueOf(roleStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CASHIER;
        }
    }
}