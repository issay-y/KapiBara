package com.cafe.kapibara.kapibara.DAO;

import com.cafe.kapibara.kapibara.config.DatabaseConnector;
import com.cafe.kapibara.kapibara.model.inventory.InventoryCategory;
import com.cafe.kapibara.kapibara.model.inventory.InventoryForm;
import com.cafe.kapibara.kapibara.model.inventory.InventoryItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Queries for the inventory pages.
 *
 * Two tables:
 *   inventory_categories (category_id, name)            the list of categories
 *   inventory_items      (..., category_id -> categories) the stock items
 *
 * Both are created automatically. If an older inventory_items table with a text
 * "category" column is found, it is migrated once: every distinct category is copied
 * into inventory_categories, each item gets its category_id, and the old column is dropped.
 * Nothing is seeded from code.
 */
public class InventoryDAO {

    private static volatile boolean ready = false;

    private static final String CREATE_CATEGORIES =
            "CREATE TABLE IF NOT EXISTS inventory_categories ("
                    + "category_id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(50) NOT NULL UNIQUE, "
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)";

    private static final String CREATE_ITEMS =
            "CREATE TABLE IF NOT EXISTS inventory_items ("
                    + "item_id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(100) NOT NULL, "
                    + "category_id INT NOT NULL, "
                    + "quantity DECIMAL(10,2) NOT NULL DEFAULT 0, "
                    + "unit VARCHAR(20) NOT NULL, "
                    + "minimum_stock DECIMAL(10,2) NOT NULL DEFAULT 0, "
                    + "notes VARCHAR(255) NULL, "
                    + "image_path VARCHAR(255) NULL, "
                    + "updated_by VARCHAR(100) NULL, "
                    + "updated_by VARCHAR(100) NULL, "
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "CONSTRAINT fk_inventory_items_category FOREIGN KEY (category_id) "
                    + "REFERENCES inventory_categories (category_id) ON DELETE RESTRICT)";

    // =====================================================================
    //  READ: ITEMS
    // =====================================================================

    public List<InventoryItem> findAll() throws SQLException {
        String sql = "SELECT i.item_id, i.name, c.name AS category, i.quantity, i.unit, i.minimum_stock, i.updated_at "
                + "FROM inventory_items i JOIN inventory_categories c ON c.category_id = i.category_id "
                + "ORDER BY i.item_id";

        List<InventoryItem> items = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp updated = rs.getTimestamp("updated_at");
                    items.add(new InventoryItem(
                            rs.getLong("item_id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getDouble("quantity"),
                            rs.getString("unit"),
                            rs.getDouble("minimum_stock"),
                            updated == null ? LocalDateTime.now() : updated.toLocalDateTime()));
                }
            }
        }
        return items;
    }

    public Optional<InventoryForm> findById(long id) throws SQLException {
        String sql = "SELECT i.item_id, i.name, c.name AS category, i.quantity, i.unit, i.minimum_stock, "
                + "i.notes, i.image_path, i.updated_by, i.updated_at "
                + "FROM inventory_items i JOIN inventory_categories c ON c.category_id = i.category_id "
                + "WHERE i.item_id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    Timestamp updated = rs.getTimestamp("updated_at");
                    return Optional.of(new InventoryForm(
                            rs.getLong("item_id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getDouble("quantity"),
                            rs.getString("unit"),
                            rs.getDouble("minimum_stock"),
                            rs.getString("notes"),
                            rs.getString("updated_by"),
                            updated == null ? null : updated.toLocalDateTime(),
                            rs.getString("image_path")));
                }
            }
        }
    }

    /** Case-insensitive. Pass the item's own id as excludeId when editing (0 when adding). */
    public boolean nameExists(String name, long excludeId) throws SQLException {
        String sql = "SELECT 1 FROM inventory_items WHERE LOWER(name) = LOWER(?) AND item_id <> ? LIMIT 1";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setLong(2, excludeId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    // =====================================================================
    //  WRITE: ITEMS
    // =====================================================================

    public void insert(InventoryForm form, String updatedBy) throws SQLException {
        String sql = "INSERT INTO inventory_items "
                + "(name, category_id, quantity, unit, minimum_stock, notes, image_path, updated_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            long categoryId = categoryId(conn, form.category());
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, form.name());
                ps.setLong(2, categoryId);
                ps.setDouble(3, form.quantity());
                ps.setString(4, form.unit());
                ps.setDouble(5, form.minimum());
                ps.setString(6, form.notes());
                ps.setString(7, form.imagePath());
                ps.setString(8, updatedBy);
                ps.executeUpdate();
            }
        }
    }

    public void update(InventoryForm form, String updatedBy) throws SQLException {
        String sql = "UPDATE inventory_items SET name = ?, category_id = ?, quantity = ?, unit = ?, "
                + "minimum_stock = ?, notes = ?, image_path = ?, updated_by = ?, updated_at = NOW() "
                + "WHERE item_id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            long categoryId = categoryId(conn, form.category());
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, form.name());
                ps.setLong(2, categoryId);
                ps.setDouble(3, form.quantity());
                ps.setString(4, form.unit());
                ps.setDouble(5, form.minimum());
                ps.setString(6, form.notes());
                ps.setString(7, form.imagePath());
                ps.setString(8, updatedBy);
                ps.setLong(9, form.id());
                ps.executeUpdate();
            }
        }
    }

    /** @return true if a row was deleted */
    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM inventory_items WHERE item_id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        }
    }

    // =====================================================================
    //  CATEGORIES
    // =====================================================================

    /** Every category name, alphabetical: used by the filter and the form combo boxes. */
    public List<String> findCategories() throws SQLException {
        String sql = "SELECT name FROM inventory_categories ORDER BY name";
        List<String> categories = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) categories.add(rs.getString(1));
            }
        }
        return categories;
    }

    /** Every category with the number of items using it: used by the Manage categories dialog. */
    public List<InventoryCategory> findCategoryUsage() throws SQLException {
        String sql = "SELECT c.category_id, c.name, COUNT(i.item_id) AS item_count "
                + "FROM inventory_categories c LEFT JOIN inventory_items i ON i.category_id = c.category_id "
                + "GROUP BY c.category_id, c.name ORDER BY c.name";
        List<InventoryCategory> list = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new InventoryCategory(rs.getLong("category_id"), rs.getString("name"),
                            rs.getInt("item_count")));
                }
            }
        }
        return list;
    }

    /** Case-insensitive. */
    public boolean categoryNameExists(String name) throws SQLException {
        String sql = "SELECT 1 FROM inventory_categories WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    public void insertCategory(String name) throws SQLException {
        String sql = "INSERT INTO inventory_categories (name) VALUES (?)";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        }
    }


    public boolean deleteCategory(long id) throws SQLException {
        String sql = "DELETE FROM inventory_categories WHERE category_id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        }
    }

    private static long categoryId(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT category_id FROM inventory_categories WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("The category \"" + name + "\" no longer exists. Please choose another one.");
    }

    // =====================================================================
    //  SCHEMA (+ one-time migration of the old text category column)
    // =====================================================================

    private static void ensureSchema(Connection conn) throws SQLException {
        if (ready) return;
        synchronized (InventoryDAO.class) {
            if (ready) return;

            try (Statement st = conn.createStatement()) {
                st.execute(CREATE_CATEGORIES);
                st.execute(CREATE_ITEMS);
            }
            if (hasColumn(conn, "inventory_items", "category")) {
                migrateTextCategories(conn);
            }
            if (!hasColumn(conn, "inventory_items", "image_path")) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("ALTER TABLE inventory_items ADD COLUMN image_path VARCHAR(255) NULL AFTER notes");
                }
            }
            ready = true;
        }
    }

    private static void migrateTextCategories(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT IGNORE INTO inventory_categories (name) "
                    + "SELECT DISTINCT TRIM(category) FROM inventory_items "
                    + "WHERE category IS NOT NULL AND TRIM(category) <> ''");

            if (!hasColumn(conn, "inventory_items", "category_id")) {
                st.executeUpdate("ALTER TABLE inventory_items ADD COLUMN category_id INT NULL AFTER name");
            }
            st.executeUpdate("UPDATE inventory_items i JOIN inventory_categories c ON c.name = TRIM(i.category) "
                    + "SET i.category_id = c.category_id");

            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM inventory_items WHERE category_id IS NULL")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    st.executeUpdate("INSERT IGNORE INTO inventory_categories (name) VALUES ('Uncategorized')");
                    st.executeUpdate("UPDATE inventory_items SET category_id = "
                            + "(SELECT category_id FROM inventory_categories WHERE name = 'Uncategorized') "
                            + "WHERE category_id IS NULL");
                }
            }

            st.executeUpdate("ALTER TABLE inventory_items MODIFY category_id INT NOT NULL");
            st.executeUpdate("ALTER TABLE inventory_items ADD CONSTRAINT fk_inventory_items_category "
                    + "FOREIGN KEY (category_id) REFERENCES inventory_categories (category_id) ON DELETE RESTRICT");
            st.executeUpdate("ALTER TABLE inventory_items DROP COLUMN category");
        }
    }

    private static boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table.toLowerCase());
            ps.setString(2, column.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }
}