package com.cafe.kapibara.kapibara.model.inventory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Sample inventory. TODO: replace with an InventoryDAO that reads the real table. */
public final class InventoryData {

    private InventoryData() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static List<InventoryItem> sample() {
        long id = 1;
        List<InventoryItem> items = new ArrayList<>();
        items.add(item(id++, "Espresso beans", "Coffee", 12, "kg", 5, 9, 30));
        items.add(item(id++, "Fresh milk", "Dairy", 8, "liters", 12, 10, 20));
        items.add(item(id++, "Cocoa powder", "Non-Coffee", 2, "kg", 3, 8, 45));
        items.add(item(id++, "Takeaway cups", "Supplies", 18, "pcs", 50, 10, 15));
        items.add(item(id++, "Caramel syrup", "Add-ons", 0, "liters", 2, 9, 50));
        items.add(item(id++, "Milk foam mix", "Dairy", 0, "packs", 4, 9, 40));
        items.add(item(id++, "Matcha powder", "Non-Coffee", 1, "kg", 2, 9, 10));
        items.add(item(id++, "Decaf beans", "Coffee", 6, "kg", 3, 8, 30));
        items.add(item(id++, "Vanilla syrup", "Add-ons", 5, "liters", 2, 8, 35));
        items.add(item(id++, "Hazelnut syrup", "Add-ons", 4, "liters", 2, 8, 40));
        items.add(item(id++, "Oat milk", "Dairy", 14, "liters", 6, 8, 50));
        items.add(item(id++, "Heavy cream", "Dairy", 9, "liters", 4, 8, 55));
        items.add(item(id++, "White sugar", "Supplies", 20, "kg", 8, 9, 0));
        items.add(item(id++, "Brown sugar", "Supplies", 12, "kg", 5, 9, 5));
        items.add(item(id++, "Chocolate sauce", "Add-ons", 6, "liters", 3, 9, 15));
        items.add(item(id++, "Whipped cream", "Dairy", 10, "cans", 4, 9, 20));
        items.add(item(id++, "Earl Grey tea bags", "Non-Coffee", 120, "pcs", 50, 9, 25));
        items.add(item(id++, "Chai concentrate", "Non-Coffee", 7, "liters", 3, 8, 20));
        items.add(item(id++, "Paper straws", "Supplies", 400, "pcs", 150, 8, 15));
        items.add(item(id++, "Cup lids", "Supplies", 300, "pcs", 100, 8, 10));
        items.add(item(id++, "Paper napkins", "Supplies", 900, "pcs", 300, 8, 5));
        items.add(item(id++, "Croissant dough", "Pastry", 40, "pcs", 20, 8, 0));
        items.add(item(id++, "Butter", "Dairy", 15, "kg", 5, 7, 55));
        items.add(item(id++, "Muffin mix", "Pastry", 6, "kg", 3, 7, 50));
        return items;
    }

    private static InventoryItem item(long id, String name, String category, double qty,
                                      String unit, double min, int hour, int minute) {
        return new InventoryItem(id, name, category, qty, unit, min,
                LocalDate.now().atTime(hour, minute));
    }
}