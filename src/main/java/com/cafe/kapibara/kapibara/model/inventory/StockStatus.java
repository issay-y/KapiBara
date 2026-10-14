package com.cafe.kapibara.kapibara.model.inventory;

/** How an inventory item is doing against its minimum stock level. */
public enum StockStatus {

    IN_STOCK("In stock", "adm-pill-green", 3),
    LOW("Low stock", "adm-pill-gold", 2),
    CRITICAL("Critical", "adm-pill-red", 1),
    OUT_OF_STOCK("Out of stock", "adm-pill-red", 0);

    private final String displayName;
    private final String pillStyleClass;
    private final int severity; // lower = more urgent

    StockStatus(String displayName, String pillStyleClass, int severity) {
        this.displayName = displayName;
        this.pillStyleClass = pillStyleClass;
        this.severity = severity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPillStyleClass() {
        return pillStyleClass;
    }

    public int getSeverity() {
        return severity;
    }

    /**
     * Out of stock at 0, in stock when above the minimum, critical below half the minimum,
     * otherwise low (at or below the minimum).
     */
    public static StockStatus of(double quantity, double minimum) {
        if (quantity <= 0) return OUT_OF_STOCK;
        if (quantity > minimum) return IN_STOCK;
        if (quantity < minimum * 0.5) return CRITICAL;
        return LOW;
    }
}