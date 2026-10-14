package com.cafe.kapibara.kapibara.model.account;

public enum AccountStatus {
    ACTIVE("Active"), INACTIVE("Inactive");
    private final String displayName;
    AccountStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}