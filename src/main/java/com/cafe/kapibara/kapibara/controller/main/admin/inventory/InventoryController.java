package com.cafe.kapibara.kapibara.controller.main.admin.inventory;

import com.cafe.kapibara.kapibara.DAO.InventoryDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.model.inventory.InventoryItem;
import com.cafe.kapibara.kapibara.model.inventory.StockStatus;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import com.cafe.kapibara.kapibara.util.TableRowUtil;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TreeSet;

public class InventoryController implements Initializable, AdminShell.Aware {

    private static final int PAGE_SIZE = 8;
    private static final String ALL_CATEGORIES = "All categories";
    private static final String ALL_STATUSES = "All statuses";
    private static final DateTimeFormatter UPDATED =
            DateTimeFormatter.ofPattern("dd MMM · h:mm a", Locale.ENGLISH);

    // Sortable columns (grid column index)
    private static final int COL_NAME = 0, COL_CATEGORY = 1, COL_STATUS = 4, COL_UPDATED = 5;

    // ---- Filter memory --------------------------------------------------
    // This page is rebuilt every time you come back to it (for example after
    // editing an item), so the filters, sort and page are remembered here instead.
    private static String savedSearch = "";
    private static String savedCategory = ALL_CATEGORIES;
    private static String savedStatus = ALL_STATUSES;
    private static int savedPage = 0;
    private static int savedSortCol = -1;
    private static boolean savedSortAsc = true;

    // header
    @FXML private Label pageTitle, pageSub;
    @FXML private Button addItemBtn;

    // KPI cards
    @FXML private Label totalValue, totalSub;
    @FXML private Label inStockValue, inStockSub;
    @FXML private Label lowValue, lowSub;
    @FXML private Label outValue, outSub;

    // warning banner
    @FXML private HBox alertBanner;
    @FXML private Label alertTitle, alertSub;

    // filters
    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Button archiveToggleBtn;

    // table + paging
    @FXML private GridPane inventoryGrid;
    @FXML private Label showingLabel;
    @FXML private HBox paginationBox;

    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final List<InventoryItem> allItems = new ArrayList<>();        // active items
    private final List<InventoryItem> archivedItems = new ArrayList<>();   // archived items
    private List<InventoryItem> filtered = List.of();
    private List<String> categoryNames = List.of();   // from the inventory_categories table
    private boolean showArchived = false;             // false = active items, true = archived items
    private int page = 0;
    private int pendingPage = -1;          // page to return to after (re)loading
    private boolean suppressFilter = false; // true while the filter controls are rebuilt in code
    private boolean extrasMounted = false;
    private int sortCol = savedSortCol;
    private boolean sortAsc = savedSortAsc;
    private AdminShell shell;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        categoryCombo.getItems().add(ALL_CATEGORIES);

        statusCombo.getItems().add(ALL_STATUSES);
        for (StockStatus s : StockStatus.values()) {
            statusCombo.getItems().add(s.getDisplayName());
        }

        // Restore what was selected before leaving the page (set BEFORE the listeners exist).
        searchField.setText(savedSearch);
        categoryCombo.setValue(savedCategory);   // the full list is filled in once the items load
        statusCombo.setValue(statusCombo.getItems().contains(savedStatus) ? savedStatus : ALL_STATUSES);
        page = savedPage;

        searchField.textProperty().addListener((obs, old, now) -> { if (!suppressFilter) applyFilters(); });
        categoryCombo.valueProperty().addListener((obs, old, now) -> { if (!suppressFilter) applyFilters(); });
        statusCombo.valueProperty().addListener((obs, old, now) -> { if (!suppressFilter) applyFilters(); });

        inventoryGrid.getStyleClass().add("inv-grid");   // compact rows (see CSS)

        updateModeTexts();
        loadItems();
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;
        mountExtras();
    }

    /** Small look-and-feel hooks that don't need FXML changes. */
    private void mountExtras() {
        if (extrasMounted) return;
        extrasMounted = true;

        // Brown outlines on the search box, dropdowns and buttons (same as the Transactions page)
        for (Node n = inventoryGrid; n != null; n = n.getParent()) {
            if (n.getStyleClass().contains("adm-lg") || n.getStyleClass().contains("adm-page")) {
                if (!n.getStyleClass().contains("adm-tx")) n.getStyleClass().add("adm-tx");
                break;
            }
        }

        // "View Low Stock →" link inside the warning banner
        if (alertTitle.getParent() instanceof Pane box) {
            Button link = new Button("View Low Stock →");
            link.getStyleClass().add("adm-link");
            link.setOnAction(this::handleViewLowStock);
            box.getChildren().add(link);
        }
    }

    // =====================================================================
    //  ACTIVE / ARCHIVED MODE
    // =====================================================================

    /** Updates the title, subtitle and buttons for the current mode. */
    private void updateModeTexts() {
        if (showArchived) {
            pageTitle.setText("Archived items");
            pageSub.setText("Archived items are hidden from inventory. Restore an item to bring it back.");
            archiveToggleBtn.setText("←   Back to inventory");
        } else {
            pageTitle.setText("Inventory");
            pageSub.setText("Keep the essentials stocked and the counter running smoothly.");
            archiveToggleBtn.setText(archivedItems.isEmpty()
                    ? "🗃   Archived"
                    : "🗃   Archived (" + archivedItems.size() + ")");
        }
        addItemBtn.setVisible(!showArchived);
        addItemBtn.setManaged(!showArchived);
    }

    @FXML
    private void handleToggleArchive(ActionEvent event) {
        showArchived = !showArchived;
        pendingPage = -1;   // go back to page 1 when switching
        updateModeTexts();
        applyFilters();
    }

    // =====================================================================
    //  LOADING (database work happens off the UI thread)
    // =====================================================================

    private void loadItems() {
        pendingPage = page;   // stay on the same page after reloading

        Thread worker = new Thread(() -> {
            List<InventoryItem> items = null;
            List<InventoryItem> archived = null;
            List<String> categories = List.of();
            Throwable failure = null;
            try {
                items = inventoryDAO.findAll();            // active items only
                archived = inventoryDAO.findArchived();    // archived items only
                categories = inventoryDAO.findCategories();
            } catch (Throwable t) {
                failure = t;
            }

            final List<InventoryItem> finalItems = items;
            final List<InventoryItem> finalArchived = archived;
            final List<String> finalCategories = categories;
            final Throwable finalFailure = failure;

            Platform.runLater(() -> {
                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    showLoadError();
                    return;
                }
                allItems.clear();
                allItems.addAll(finalItems);
                archivedItems.clear();
                archivedItems.addAll(finalArchived);
                categoryNames = finalCategories;

                // nothing left to show in the archive: go back to the normal view
                if (showArchived && archivedItems.isEmpty()) showArchived = false;

                updateModeTexts();
                refreshCategories();
                loadKpis();
                applyFilters();
            });
        }, "load-inventory");
        worker.setDaemon(true);
        worker.start();
    }

    private void showLoadError() {
        inventoryGrid.getChildren().clear();
        Label error = new Label("Could not load inventory. Please make sure the database is running.");
        error.getStyleClass().add("adm-td");
        error.setMaxWidth(Double.MAX_VALUE);
        inventoryGrid.add(error, 0, 0, 7, 1);
        showingLabel.setText("");
        paginationBox.getChildren().clear();
    }

    /**
     * Rebuilds the category filter from the categories table, keeping the current choice if it still exists.
     * The filter listeners are paused so rebuilding the list doesn't reset the selection or the page.
     */
    private void refreshCategories() {
        String selected = categoryCombo.getValue();

        List<String> options = new ArrayList<>();
        options.add(ALL_CATEGORIES);
        options.addAll(categoryNames);

        suppressFilter = true;
        try {
            categoryCombo.getItems().setAll(options);
            categoryCombo.setValue(selected != null && options.contains(selected) ? selected : ALL_CATEGORIES);
        } finally {
            suppressFilter = false;
        }
    }

    // =====================================================================
    //  KPIs + BANNER (always based on active items)
    // =====================================================================

    private void loadKpis() {
        int total = allItems.size();
        int inStock = count(StockStatus.IN_STOCK);
        int low = count(StockStatus.LOW);
        int out = count(StockStatus.OUT_OF_STOCK);

        totalValue.setText(String.valueOf(total));
        totalSub.setText("Tracked inventory items");

        inStockValue.setText(String.valueOf(inStock));
        inStockSub.setText("Above minimum stock");

        lowValue.setText(String.valueOf(low));
        lowSub.setText("At or below minimum stock");

        outValue.setText(String.valueOf(out));
        outSub.setText("Replenishment required");

        if (low + out == 0) {
            alertBanner.setVisible(false);
            alertBanner.setManaged(false);
            return;
        }
        alertBanner.setVisible(true);
        alertBanner.setManaged(true);

        alertTitle.setText(low + (low == 1 ? " item is" : " items are") + " below minimum stock; "
                + out + (out == 1 ? " is" : " are") + " out of stock");

        // most urgent first: out of stock, then low stock; show 3 names and "and N more"
        List<String> names = new ArrayList<>();
        for (InventoryItem item : allItems) {
            if (item.status() == StockStatus.OUT_OF_STOCK) names.add(titleCase(item.name()));
        }
        for (InventoryItem item : allItems) {
            if (item.status() == StockStatus.LOW) names.add(titleCase(item.name()));
        }
        alertSub.setText(summary(names) + " need restocking.");
    }

    private int count(StockStatus status) {
        int n = 0;
        for (InventoryItem item : allItems) {
            if (item.status() == status) n++;
        }
        return n;
    }

    /** "A, B, C and 9 more" (or "A and B" for short lists). */
    private static String summary(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        if (names.size() <= 3) {
            return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
        }
        int more = names.size() - 3;
        return String.join(", ", names.subList(0, 3)) + " and " + more + " more";
    }

    /** "CARAMEL SYRUP" -> "Caramel Syrup" (display only; the saved name is not changed). */
    private static String titleCase(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        boolean startOfWord = true;
        for (char c : text.trim().toCharArray()) {
            if (Character.isWhitespace(c) || c == '-' || c == '/' || c == '(') {
                startOfWord = true;
                out.append(c);
            } else {
                out.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
                startOfWord = false;
            }
        }
        return out.toString();
    }

    // =====================================================================
    //  FILTER + SORT + TABLE
    // =====================================================================

    private void applyFilters() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String category = categoryCombo.getValue();
        String status = statusCombo.getValue();

        // remember the filters for the next time this page is opened
        savedSearch = searchField.getText() == null ? "" : searchField.getText();
        savedCategory = category == null ? ALL_CATEGORIES : category;
        savedStatus = status == null ? ALL_STATUSES : status;

        List<InventoryItem> source = showArchived ? archivedItems : allItems;

        List<InventoryItem> result = new ArrayList<>(source.stream()
                .filter(i -> q.isEmpty()
                        || i.name().toLowerCase(Locale.ROOT).contains(q)
                        || i.category().toLowerCase(Locale.ROOT).contains(q))
                .filter(i -> category == null || ALL_CATEGORIES.equals(category) || i.category().equals(category))
                .filter(i -> status == null || ALL_STATUSES.equals(status) || i.status().getDisplayName().equals(status))
                .toList());

        Comparator<InventoryItem> order = comparatorFor(sortCol);
        if (order != null) {
            result.sort(sortAsc ? order : order.reversed());
        }
        filtered = result;

        // a reload keeps the current page; a changed filter goes back to page 1
        page = pendingPage >= 0 ? pendingPage : 0;
        pendingPage = -1;
        render();
    }

    private static Comparator<InventoryItem> comparatorFor(int col) {
        return switch (col) {
            case COL_NAME -> Comparator.comparing(i -> i.name().toLowerCase(Locale.ROOT));
            case COL_CATEGORY -> Comparator.comparing(i -> i.category().toLowerCase(Locale.ROOT));
            case COL_STATUS -> Comparator.comparing(InventoryItem::status);
            case COL_UPDATED -> Comparator.comparing(InventoryItem::lastUpdated);
            default -> null;
        };
    }

    private void sortBy(int col) {
        if (sortCol == col) {
            sortAsc = !sortAsc;
        } else {
            sortCol = col;
            sortAsc = true;
        }
        savedSortCol = sortCol;
        savedSortAsc = sortAsc;
        page = 0;
        applyFilters();
    }

    private void render() {
        // clamp the page in case items were removed
        int lastPage = Math.max(0, (int) Math.ceil(filtered.size() / (double) PAGE_SIZE) - 1);
        if (page > lastPage) page = lastPage;
        savedPage = page;

        inventoryGrid.getChildren().clear();

        String[] headers = {"Item name", "Category", "Qty", "Unit", "Status", "Last updated", "Actions"};
        for (int c = 0; c < headers.length; c++) {
            final int col = c;
            boolean sortable = c == COL_NAME || c == COL_CATEGORY || c == COL_STATUS || c == COL_UPDATED;
            String text = headers[c];
            if (sortable && c == sortCol) text += sortAsc ? "  ▲" : "  ▼";

            Label h = new Label(text);
            h.getStyleClass().add("adm-th");
            h.setMaxWidth(Double.MAX_VALUE);
            if (sortable) {
                h.setCursor(Cursor.HAND);
                h.setOnMouseClicked(e -> sortBy(col));
                h.setTooltip(new Tooltip("Sort by " + headers[c].toLowerCase(Locale.ROOT)));
            }
            inventoryGrid.add(h, c, 0);
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, filtered.size());

        if (filtered.isEmpty()) {
            List<InventoryItem> source = showArchived ? archivedItems : allItems;
            String message;
            if (!source.isEmpty()) {
                message = showArchived
                        ? "No archived items match your filters."
                        : "No inventory items match your filters.";
            } else {
                message = showArchived
                        ? "No archived items."
                        : "No inventory items yet. Click \"Add item\" to get started.";
            }
            Label empty = new Label(message);
            empty.getStyleClass().add("adm-td");
            empty.setMaxWidth(Double.MAX_VALUE);
            inventoryGrid.add(empty, 0, 1, headers.length, 1);
            showingLabel.setText("No items to show");
        } else {
            for (int i = from; i < to; i++) {
                addRow(i - from + 1, filtered.get(i));
            }
            showingLabel.setText("Showing " + (from + 1) + "–" + to + " of " + filtered.size()
                    + (showArchived ? " archived items" : " inventory items"));
        }

        TableRowUtil.polish(inventoryGrid, headers.length);   // stretch cells + straight row lines
        buildPagination();
    }

    private void addRow(int row, InventoryItem item) {
        boolean alt = row % 2 == 0;   // every other row gets a slightly darker tint

        inventoryGrid.add(tint(cell(titleCase(item.name())), alt), 0, row);
        inventoryGrid.add(tint(cell(item.category()), alt), 1, row);
        inventoryGrid.add(tint(cell(item.quantityText()), alt), 2, row);
        inventoryGrid.add(tint(cell(item.unit()), alt), 3, row);

        Label pill = new Label(item.status().getDisplayName());
        pill.getStyleClass().add(item.status().getPillStyleClass());
        inventoryGrid.add(tint(wrap(pill), alt), 4, row);

        inventoryGrid.add(tint(cell(item.lastUpdated().format(UPDATED)), alt), 5, row);

        HBox actions;
        if (showArchived) {
            Button restore = iconButton(restoreIcon(), "Restore item", false);
            restore.setOnAction(e -> handleRestore(item));
            actions = new HBox(8, restore);
        } else {
            Button edit = iconButton(pencilIcon(), "Edit item", false);
            edit.setOnAction(e -> handleEdit(item));

            Button archive = iconButton(archiveIcon(), "Archive item", true);
            archive.setOnAction(e -> handleArchive(item));
            actions = new HBox(8, edit, archive);
        }
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("adm-td");
        inventoryGrid.add(tint(actions, alt), 6, row);
    }

    private static <T extends Node> T tint(T node, boolean alt) {
        if (alt) node.getStyleClass().add("inv-alt");
        return node;
    }

    private static Label cell(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("adm-td");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static HBox wrap(Node node) {
        HBox box = new HBox(node);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("adm-td");
        return box;
    }

    // ---- icons (vector shapes, no icon font needed) ----------------------

    private static Button iconButton(SVGPath icon, String tooltip, boolean danger) {
        Button b = new Button();
        b.setGraphic(icon);
        b.getStyleClass().add("tx-icon-btn");
        if (danger) b.getStyleClass().add("tx-icon-btn-danger");
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    private static SVGPath pencilIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M0 12 L0.6 8.6 L8.6 0.6 C9.1 0.1 9.9 0.1 10.4 0.6 L11.4 1.6 C11.9 2.1 11.9 2.9 11.4 3.4 "
                + "L3.4 11.4 Z");
        p.getStyleClass().add("tx-icon");
        return p;
    }

    /** An archive box: a lid on top and a body with a slot cut out. */
    private static SVGPath archiveIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M0 0.5 H12 V3.5 H0 Z M1 4.5 H11 V12 H1 Z M4 6.5 H8 V8 H4 Z");
        p.setFillRule(FillRule.EVEN_ODD);
        p.getStyleClass().add("tx-icon");
        return p;
    }

    /** A curved "undo" arrow. */
    private static SVGPath restoreIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M4.5 0 L0 4 L4.5 8 V5.2 C7.5 5.2 9.6 6.3 11 9.5 C10.6 5.2 8 2.8 4.5 2.8 Z");
        p.getStyleClass().add("tx-icon");
        return p;
    }

    // =====================================================================
    //  PAGINATION
    // =====================================================================

    private void buildPagination() {
        paginationBox.getChildren().clear();

        int totalPages = (int) Math.ceil(filtered.size() / (double) PAGE_SIZE);
        if (totalPages == 0) return;
        int last = totalPages - 1;

        Button prev = new Button("Previous");
        prev.getStyleClass().add("adm-page-btn");
        prev.setDisable(page == 0);
        prev.setOnAction(e -> goTo(page - 1));
        paginationBox.getChildren().add(prev);

        TreeSet<Integer> shown = new TreeSet<>();
        shown.add(0);
        shown.add(last);
        for (int p = page - 1; p <= page + 1; p++) {
            if (p >= 0 && p <= last) shown.add(p);
        }

        int previous = -1;
        for (int p : shown) {
            if (previous != -1 && p - previous > 1) {
                Label dots = new Label("…");
                dots.getStyleClass().add("adm-ellipsis");
                paginationBox.getChildren().add(dots);
            }
            Button number = new Button(String.valueOf(p + 1));
            number.getStyleClass().add(p == page ? "adm-page-num-active" : "adm-page-num");
            number.setOnAction(e -> goTo(p));
            paginationBox.getChildren().add(number);
            previous = p;
        }

        Button next = new Button("Next");
        next.getStyleClass().add("adm-page-btn");
        next.setDisable(page == last);
        next.setOnAction(e -> goTo(page + 1));
        paginationBox.getChildren().add(next);
    }

    private void goTo(int newPage) {
        page = newPage;
        render();
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================

    @FXML
    private void handleAddItem(ActionEvent event) {
        if (shell != null) shell.navigate(AdminPage.INVENTORY_ADD);
    }

    @FXML
    private void handleManageCategories(ActionEvent event) {
        CategoryManagerDialog.show(inventoryGrid, this::loadItems);
    }

    @FXML
    private void handleViewLowStock(ActionEvent event) {
        if (shell != null) shell.navigate(AdminPage.LOW_STOCK);
    }

    private void handleEdit(InventoryItem item) {
        if (shell != null) shell.navigate(AdminPage.INVENTORY_EDIT, item.id());
    }

    private void handleArchive(InventoryItem item) {
        boolean confirmed = AlertsUtil.confirm(inventoryGrid, AlertsUtil.Type.WARNING, "Archive this item?",
                "Archive " + item.name() + "?\nIt will be hidden from the inventory and the cashier screen. "
                        + "You can restore it any time from the Archived list.", "Archive");
        if (!confirmed) return;

        final String user = shell == null ? null : shell.getCurrentUserName();
        runAndReload("archive-inventory-item", "Archive failed", "Could not archive the item: ",
                () -> inventoryDAO.archive(item.id(), user));
    }

    private void handleRestore(InventoryItem item) {
        boolean confirmed = AlertsUtil.confirm(inventoryGrid, AlertsUtil.Type.CONFIRMATION, "Restore this item?",
                "Restore " + item.name() + " to the inventory?", "Restore");
        if (!confirmed) return;

        final String user = shell == null ? null : shell.getCurrentUserName();
        runAndReload("restore-inventory-item", "Restore failed", "Could not restore the item: ",
                () -> inventoryDAO.restore(item.id(), user));
    }

    /** Runs a database change off the UI thread, then reloads the lists (or shows an error). */
    private void runAndReload(String threadName, String errorTitle, String errorPrefix, DbAction action) {
        Thread worker = new Thread(() -> {
            Throwable failure = null;
            try {
                action.run();
            } catch (Throwable t) {
                failure = t;
            }

            final Throwable finalFailure = failure;
            Platform.runLater(() -> {
                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    AlertsUtil.error(inventoryGrid, errorTitle, errorPrefix + finalFailure.getMessage());
                } else {
                    loadItems();
                }
            });
        }, threadName);
        worker.setDaemon(true);
        worker.start();
    }

    @FunctionalInterface
    private interface DbAction {
        void run() throws Exception;
    }
}