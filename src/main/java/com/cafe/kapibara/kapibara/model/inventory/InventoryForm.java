package com.cafe.kapibara.kapibara.model.inventory;

import java.time.LocalDateTime;

public record InventoryForm(long id, String name, String category, double quantity, String unit,
                            double minimum, String notes, String updatedBy, LocalDateTime updatedAt,
                            String imagePath) {

    public InventoryForm(long id, String name, String category, double quantity, String unit,
                         double minimum, String notes, String updatedBy, LocalDateTime updatedAt) {
        this(id, name, category, quantity, unit, minimum, notes, updatedBy, updatedAt, null);
    }

    public InventoryForm withImage(String image) {
        return new InventoryForm(id, name, category, quantity, unit, minimum, notes,
                updatedBy, updatedAt, image);
    }
}