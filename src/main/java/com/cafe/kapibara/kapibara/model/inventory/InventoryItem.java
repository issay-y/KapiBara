package com.cafe.kapibara.kapibara.model.inventory;

import java.time.LocalDateTime;

public record InventoryItem(long id, String name, String category, double quantity,
                            String unit, double minimum, LocalDateTime lastUpdated) {

    public StockStatus status() {
        return StockStatus.of(quantity, minimum);
    }

    public String quantityText() {
        return format(quantity);
    }

    public String minimumText() {
        return format(minimum);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}