package com.cafe.kapibara.kapibara.model.inventory;

import java.time.LocalDateTime;

/** The editable details of one inventory item (used by the Add / Edit item form). */
public record InventoryForm(long id, String name, String category, double quantity, String unit,
                            double minimum, String notes, String updatedBy, LocalDateTime updatedAt) {
}