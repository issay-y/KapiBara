package com.cafe.kapibara.kapibara.model.inventory;


public final class InventoryRules {

    public static final double MINIMUM_STOCK = 150;

    public static final double MAXIMUM_QUANTITY = 500;

    private InventoryRules() {
    }

    public static String text(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}