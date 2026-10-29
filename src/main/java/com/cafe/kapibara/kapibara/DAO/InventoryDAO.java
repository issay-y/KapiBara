package com.cafe.kapibara.kapibara.DAO;

import com.cafe.kapibara.kapibara.config.DatabaseConnector;
import com.cafe.kapibara.kapibara.model.inventory.InventoryCategory;
import com.cafe.kapibara.kapibara.model.inventory.InventoryForm;
import com.cafe.kapibara.kapibara.model.inventory.InventoryItem;
import com.cafe.kapibara.kapibara.model.inventory.InventoryRules;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class InventoryDAO {

    private static volatile boolean ready = false;

    // =====================================================================
    //  IMAGES (stored in Apache htdocs, one sub-folder per category)
    // =====================================================================

    /** Change here, or start the app with -Dkapibara.images="D:/somewhere/Kapibara". */
    private static final String DEFAULT_IMAGE_ROOT = "C:/xampp/htdocs/Kapibara";

    public static Path imageRoot() {
        return Paths.get(System.getProperty("kapibara.images", DEFAULT_IMAGE_ROOT))
                .toAbsolutePath().normalize();
    }

    /** Folder name for a category: the category name with characters Windows forbids removed. */
    private static String folderName(String category) {
        String clean = category == null ? "" : category.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        return clean.isEmpty() ? "Uncategorized" : clean;
    }

    /**
     * Resolves a stored image_path ("Coffee/abc.png") to a file on disk.
     * Old rows that only hold a file name are searched for in every category folder.
     * Returns null when there is no image or the file can't be found.
     */
    public static File imageFile(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return null;

        Path root = imageRoot();
        Path direct = root.resolve(imagePath).normalize();
        if (direct.startsWith(root) && Files.isRegularFile(direct)) return direct.toFile();

        if (!imagePath.contains("/") && !imagePath.contains("\\")) {
            try (Stream<Path> dirs = Files.list(root)) {
                for (Path dir : (Iterable<Path>) dirs.filter(Files::isDirectory)::iterator) {
                    Path candidate = dir.resolve(imagePath);
                    if (Files.isRegularFile(candidate)) return candidate.toFile();
                }
            } catch (IOException ignored) {
                // root folder missing: fall through to null
            }
        }
        return null;
    }

    /** file:/C:/xampp/... string ready for new javafx.scene.image.Image(...). Null when missing. */
    public static String imageUri(String imagePath) {
        File f = imageFile(imagePath);
        return f == null ? null : f.toURI().toString();
    }

    /**
     * Copies an uploaded picture into htdocs/Kapibara/<Category>/ under a random name.
     * @return the value to store in image_path, e.g. "Coffee/3f95...png"
     */
    public static String saveImage(File source, String category) throws IOException {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : ".png";

        String folder = folderName(category);
        Path dir = imageRoot().resolve(folder);
        Files.createDirectories(dir);

        String fileName = UUID.randomUUID() + ext;
        Files.copy(source.toPath(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return folder + "/" + fileName;
    }

    /** Deletes the picture file; quietly does nothing if it isn't there. */
    public static void deleteImage(String imagePath) {
        File f = imageFile(imagePath);
        if (f != null) {
            try {
                Files.deleteIfExists(f.toPath());
            } catch (IOException e) {
                System.err.println("Could not delete image " + f + ": " + e.getMessage());
            }
        }
    }

    // =====================================================================
    //  SCHEMA SQL
    // =====================================================================

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
                    + "price DECIMAL(10,2) NOT NULL DEFAULT 0, "
                    + "notes VARCHAR(255) NULL, "
                    + "image_path VARCHAR(255) NULL, "
                    + "archived BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "updated_by VARCHAR(100) NULL, "
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "CONSTRAINT fk_inventory_items_category FOREIGN KEY (category_id) "
                    + "REFERENCES inventory_categories (category_id) ON DELETE RESTRICT)";

    // =====================================================================
    //  READ: ITEMS
    // =====================================================================

    /** Active (not archived) items. */
    public List<InventoryItem> findAll() throws SQLException {
        return findByArchived(false);
    }

    /** Archived items only. */
    public List<InventoryItem> findArchived() throws SQLException {
        return findByArchived(true);
    }

    private List<InventoryItem> findByArchived(boolean archived) throws SQLException {
        String sql = "SELECT i.item_id, i.name, c.name AS category, i.quantity, i.unit, i.minimum_stock, i.updated_at "
                + "FROM inventory_items i JOIN inventory_categories c ON c.category_id = i.category_id "
                + "WHERE i.archived = ? "
                + "ORDER BY i.item_id";

        List<InventoryItem> items = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, archived);
                try (ResultSet rs = ps.executeQuery()) {
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

    /**
     * Case-insensitive. Archived items count too, so a name can't be reused while its
     * item is archived (restore it instead). Pass the item's own id as excludeId when editing (0 when adding).
     */
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
        insert(form, BigDecimal.ZERO, updatedBy);
    }

    public void insert(InventoryForm form, BigDecimal price, String updatedBy) throws SQLException {
        String sql = "INSERT INTO inventory_items "
                + "(name, category_id, quantity, unit, minimum_stock, price, notes, image_path, updated_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            checkQuantity(form.quantity());
            long categoryId = categoryId(conn, form.category());
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, form.name());
                ps.setLong(2, categoryId);
                ps.setDouble(3, form.quantity());
                ps.setString(4, form.unit());
                ps.setDouble(5, InventoryRules.MINIMUM_STOCK);
                ps.setBigDecimal(6, price == null ? BigDecimal.ZERO : price);
                ps.setString(7, form.notes());
                ps.setString(8, form.imagePath());
                ps.setString(9, updatedBy);
                ps.executeUpdate();
            }
        }
    }

    public void update(InventoryForm form, String updatedBy) throws SQLException {
        update(form, null, updatedBy);
    }

    /** A null price leaves the stored price unchanged. */
    public void update(InventoryForm form, BigDecimal price, String updatedBy) throws SQLException {
        String sql = "UPDATE inventory_items SET name = ?, category_id = ?, quantity = ?, unit = ?, "
                + "minimum_stock = ?, price = COALESCE(?, price), notes = ?, image_path = ?, "
                + "updated_by = ?, updated_at = NOW() "
                + "WHERE item_id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            checkQuantity(form.quantity());
            long categoryId = categoryId(conn, form.category());
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, form.name());
                ps.setLong(2, categoryId);
                ps.setDouble(3, form.quantity());
                ps.setString(4, form.unit());
                ps.setDouble(5, InventoryRules.MINIMUM_STOCK);
                ps.setBigDecimal(6, price);
                ps.setString(7, form.notes());
                ps.setString(8, form.imagePath());
                ps.setString(9, updatedBy);
                ps.setLong(10, form.id());
                ps.executeUpdate();
            }
        }
    }

    /** Hard-coded limit: one item can't hold more than InventoryRules.MAXIMUM_QUANTITY. */
    private static void checkQuantity(double quantity) throws SQLException {
        if (quantity < 0 || quantity > InventoryRules.MAXIMUM_QUANTITY) {
            throw new SQLException("The quantity must be between 0 and "
                    + InventoryRules.text(InventoryRules.MAXIMUM_QUANTITY) + ".");
        }
    }

    // ---- archive / restore ----------------------------------------------

    /** Hides the item from the inventory list and the cashier. The row and its picture are kept. */
    public boolean archive(long id, String updatedBy) throws SQLException {
        return setArchived(id, true, updatedBy);
    }

    /** Brings an archived item back. */
    public boolean restore(long id, String updatedBy) throws SQLException {
        return setArchived(id, false, updatedBy);
    }

    private boolean setArchived(long id, boolean archived, String updatedBy) throws SQLException {
        String sql = "UPDATE inventory_items SET archived = ?, updated_by = ?, updated_at = NOW() "
                + "WHERE item_id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, archived);
                ps.setString(2, updatedBy);
                ps.setLong(3, id);
                return ps.executeUpdate() > 0;
            }
        }
    }

    /**
     * Permanently deletes the row and its picture file.
     * The screens now use archive() instead; this is kept for cleanup or future use.
     * @return true if a row was deleted
     */
    public boolean delete(long id) throws SQLException {
        String imagePath = null;
        boolean deleted;
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT image_path FROM inventory_items WHERE item_id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) imagePath = rs.getString(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM inventory_items WHERE item_id = ?")) {
                ps.setLong(1, id);
                deleted = ps.executeUpdate() > 0;
            }
        }
        if (deleted) deleteImage(imagePath);
        return deleted;
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

    /**
     * Every category with the number of items using it (archived items included, because they
     * still block deleting the category): used by the Manage categories dialog.
     */
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
    //  CASHIER MENU
    // =====================================================================

    /** The stored selling price of one item (0 when none has been set). */
    public BigDecimal findPrice(long id) throws SQLException {
        String sql = "SELECT price FROM inventory_items WHERE item_id = ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    BigDecimal price = rs.next() ? rs.getBigDecimal(1) : null;
                    return price == null ? BigDecimal.ZERO : price;
                }
            }
        }
    }

    /**
     * One sellable item for the cashier screens.
     * imagePath is the stored value ("Coffee/abc.png"); use imageUri() for a loadable URI.
     */
    public record MenuRow(long id, String name, String category, double quantity,
                          String unit, double minimum, BigDecimal price, String notes, String imagePath) {

        /** file: URI of the picture in htdocs, or null when there is none. */
        public String imageUri() {
            return InventoryDAO.imageUri(imagePath);
        }
    }

    /** Items that can be sold: priced above 0 and not archived. */
    public List<MenuRow> findMenuRows() throws SQLException {
        String sql = "SELECT i.item_id, i.name, c.name AS category, i.quantity, i.unit, i.minimum_stock, i.price, i.notes, i.image_path "
                + "FROM inventory_items i JOIN inventory_categories c ON c.category_id = i.category_id "
                + "WHERE i.price > 0 AND i.archived = FALSE ORDER BY c.name, i.name";
        List<MenuRow> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new MenuRow(rs.getLong("item_id"), rs.getString("name"),
                            rs.getString("category"), rs.getDouble("quantity"),
                            rs.getString("unit"), rs.getDouble("minimum_stock"),
                            rs.getBigDecimal("price"), rs.getString("notes"),
                            rs.getString("image_path")));
                }
            }
        }
        return rows;
    }

    /** Subtracts sold quantities in one transaction; rolls everything back if any item is short. */
    public void deductStock(Map<Long, Integer> qtyById) throws SQLException {
        String sql = "UPDATE inventory_items SET quantity = quantity - ?, updated_at = NOW() "
                + "WHERE item_id = ? AND quantity >= ?";
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchema(conn);
            boolean oldAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<Long, Integer> e : qtyById.entrySet()) {
                    ps.setInt(1, e.getValue());
                    ps.setLong(2, e.getKey());
                    ps.setInt(3, e.getValue());
                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("Not enough stock left for item #" + e.getKey() + ".");
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(oldAuto);
            }
        }
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
            if (!hasColumn(conn, "inventory_items", "price")) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("ALTER TABLE inventory_items "
                            + "ADD COLUMN price DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER minimum_stock");
                }
            }
            if (!hasColumn(conn, "inventory_items", "archived")) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("ALTER TABLE inventory_items "
                            + "ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE AFTER image_path");
                }
            }
            // the minimum stock is a fixed rule: bring every row in line with it
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE inventory_items SET minimum_stock = ? WHERE minimum_stock <> ?")) {
                ps.setDouble(1, InventoryRules.MINIMUM_STOCK);
                ps.setDouble(2, InventoryRules.MINIMUM_STOCK);
                ps.executeUpdate();
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