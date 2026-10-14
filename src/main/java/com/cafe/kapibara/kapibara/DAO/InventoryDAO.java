package com.cafe.kapibara.kapibara.DAO;

import com.cafe.kapibara.kapibara.config.DatabaseConnector;
import com.cafe.kapibara.kapibara.model.inventory.InventoryData;
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
 * The inventory_items table is created automatically the first time it is needed. When the
 * table is created, it is filled once with the sample items from InventoryData so the screens
 * aren't empty. After that the database is the only source of truth.
 */
public class InventoryDAO {

    private static volatile boolean ready = false;

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS inventory_items ("
                    + "item_id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(100) NOT NULL, "
                    + "category VARCHAR(50) NOT NULL, "
                    + "quantity DECIMAL(10,2) NOT NULL DEFAULT 0, "
                    + "unit VARCHAR(20) NOT NULL, "
                    + "minimum_stock DECIMAL(10,2) NOT NULL DEFAULT 0, "
                    + "notes VARCHAR(255) NULL, "
                    + "updated_by VARCHAR(100) NULL, "
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)";

    // =====================================================================
    //  READ
    // =====================================================================

    public List<InventoryItem> findAll() throws SQLException {
        String sql = "SELECT item_id, name, category, quantity, unit, minimum_stock, updated_at "
                + "FROM inventory_items ORDER BY item_id";

        List<InventoryItem> items = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureTable(conn);
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
        String sql = "SELECT item_id, name, category, quantity, unit, minimum_stock, notes, updated_by, updated_at "
                + "FROM inventory_items WHERE item_id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureTable(conn);
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
                            updated == null ? null : updated.toLocalDateTime()));
                }
            }
        }
    }

    public List<String> findCategories() throws SQLException {
        String sql = "SELECT DISTINCT category FROM inventory_items ORDER BY category";
        List<String> categories = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) categories.add(rs.getString(1));
            }
        }
        return categories;
    }

    /** Case-insensitive. Pass the item's own id as excludeId when editing (0 when adding). */
    public boolean nameExists(String name, long excludeId) throws SQLException {
        String sql = "SELECT 1 FROM inventory_items WHERE LOWER(name) = LOWER(?) AND item_id <> ? LIMIT 1";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureTable(conn);
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
    //  WRITE
    // =====================================================================

    public void insert(InventoryForm form, String updatedBy) throws SQLException {
        String sql = "INSERT INTO inventory_items (name, category, quantity, unit, minimum_stock, notes, updated_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, form.name());
                ps.setString(2, form.category());
                ps.setDouble(3, form.quantity());
                ps.setString(4, form.unit());
                ps.setDouble(5, form.minimum());
                ps.setString(6, form.notes());
                ps.setString(7, updatedBy);
                ps.executeUpdate();
            }
        }
    }

    public void update(InventoryForm form, String updatedBy) throws SQLException {
        String sql = "UPDATE inventory_items SET name = ?, category = ?, quantity = ?, unit = ?, "
                + "minimum_stock = ?, notes = ?, updated_by = ?, updated_at = NOW() WHERE item_id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, form.name());
                ps.setString(2, form.category());
                ps.setDouble(3, form.quantity());
                ps.setString(4, form.unit());
                ps.setDouble(5, form.minimum());
                ps.setString(6, form.notes());
                ps.setString(7, updatedBy);
                ps.setLong(8, form.id());
                ps.executeUpdate();
            }
        }
    }

    /** @return true if a row was deleted */
    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM inventory_items WHERE item_id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        }
    }

    // =====================================================================
    //  SCHEMA
    // =====================================================================

    private static void ensureTable(Connection conn) throws SQLException {
        if (ready) return;
        synchronized (InventoryDAO.class) {
            if (ready) return;

            boolean existed;
            try (ResultSet rs = conn.getMetaData()
                    .getTables(conn.getCatalog(), null, "inventory_items", new String[]{"TABLE"})) {
                existed = rs.next();
            }

            if (!existed) {
                try (Statement st = conn.createStatement()) {
                    st.execute(CREATE_TABLE);
                }
                seed(conn);
            }
            ready = true;
        }
    }

    private static void seed(Connection conn) throws SQLException {
        String sql = "INSERT INTO inventory_items (name, category, quantity, unit, minimum_stock, updated_at, updated_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'System')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (InventoryItem item : InventoryData.sample()) {
                ps.setString(1, item.name());
                ps.setString(2, item.category());
                ps.setDouble(3, item.quantity());
                ps.setString(4, item.unit());
                ps.setDouble(5, item.minimum());
                ps.setTimestamp(6, Timestamp.valueOf(item.lastUpdated()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}