package com.cafe.kapibara.kapibara.controller.main.admin.inventory;

import com.cafe.kapibara.kapibara.DAO.InventoryDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.model.inventory.InventoryItem;
import com.cafe.kapibara.kapibara.model.inventory.StockStatus;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TreeSet;

public class InventoryController implements Initializable, AdminShell.Aware {

    private static final int PAGE_SIZE = 5;
    private static final String ALL_CATEGORIES = "All categories";
    private static final String ALL_STATUSES = "All statuses";
    private static final DateTimeFormatter UPDATED =
            DateTimeFormatter.ofPattern("dd MMM · h:mm a", Locale.ENGLISH);

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

    // table + paging
    @FXML private GridPane inventoryGrid;
    @FXML private Label showingLabel;
    @FXML private HBox paginationBox;

    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final List<InventoryItem> allItems = new ArrayList<>();
    private List<InventoryItem> filtered = List.of();
    private int page = 0;
    private AdminShell shell;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        categoryCombo.getItems().add(ALL_CATEGORIES);
        categoryCombo.getSelectionModel().selectFirst();

        statusCombo.getItems().add(ALL_STATUSES);
        for (StockStatus s : StockStatus.values()) {
            statusCombo.getItems().add(s.getDisplayName());
        }
        statusCombo.getSelectionModel().selectFirst();

        searchField.textProperty().addListener((obs, old, now) -> applyFilters());
        categoryCombo.valueProperty().addListener((obs, old, now) -> applyFilters());
        statusCombo.valueProperty().addListener((obs, old, now) -> applyFilters());

        loadItems();
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;
    }

    // =====================================================================
    //  LOADING (database work happens off the UI thread)
    // =====================================================================

    private void loadItems() {
        Thread worker = new Thread(() -> {
            List<InventoryItem> items = null;
            Throwable failure = null;
            try {
                items = inventoryDAO.findAll();
            } catch (Throwable t) {
                failure = t;
            }

            final List<InventoryItem> finalItems = items;
            final Throwable finalFailure = failure;

            Platform.runLater(() -> {
                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    showLoadError();
                    return;
                }
                allItems.clear();
                allItems.addAll(finalItems);
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

    /** Rebuilds the category filter from the items, keeping the current choice if it still exists. */
    private void refreshCategories() {
        String selected = categoryCombo.getValue();

        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (InventoryItem item : allItems) categories.add(item.category());

        List<String> options = new ArrayList<>();
        options.add(ALL_CATEGORIES);
        options.addAll(categories);
        categoryCombo.getItems().setAll(options);

        categoryCombo.setValue(selected != null && options.contains(selected) ? selected : ALL_CATEGORIES);
    }

    // =====================================================================
    //  KPIs + BANNER
    // =====================================================================

    private void loadKpis() {
        int total = allItems.size();
        int inStock = count(StockStatus.IN_STOCK);
        int critical = count(StockStatus.CRITICAL);
        int low = count(StockStatus.LOW) + critical;   // "low stock" includes critical items
        int out = count(StockStatus.OUT_OF_STOCK);

        totalValue.setText(String.valueOf(total));
        totalSub.setText("Tracked inventory items");

        inStockValue.setText(String.valueOf(inStock));
        inStockSub.setText("Above minimum stock");

        lowValue.setText(String.valueOf(low));
        lowSub.setText("Includes " + critical + " critical item" + (critical == 1 ? "" : "s"));

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

        List<String> names = new ArrayList<>();
        for (InventoryItem item : allItems) {
            StockStatus s = item.status();
            if (s == StockStatus.LOW || s == StockStatus.CRITICAL) names.add(item.name().toLowerCase(Locale.ROOT));
        }
        if (names.isEmpty()) {
            for (InventoryItem item : allItems) {
                if (item.status() == StockStatus.OUT_OF_STOCK) names.add(item.name().toLowerCase(Locale.ROOT));
            }
        }
        alertSub.setText(join(names) + " need restocking. View the Low Stock list for priorities.");
    }

    private int count(StockStatus status) {
        int n = 0;
        for (InventoryItem item : allItems) {
            if (item.status() == status) n++;
        }
        return n;
    }

    private static String join(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    // =====================================================================
    //  FILTER + TABLE
    // =====================================================================

    private void applyFilters() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String category = categoryCombo.getValue();
        String status = statusCombo.getValue();

        filtered = allItems.stream()
                .filter(i -> q.isEmpty()
                        || i.name().toLowerCase(Locale.ROOT).contains(q)
                        || i.category().toLowerCase(Locale.ROOT).contains(q))
                .filter(i -> category == null || ALL_CATEGORIES.equals(category) || i.category().equals(category))
                .filter(i -> status == null || ALL_STATUSES.equals(status) || i.status().getDisplayName().equals(status))
                .toList();

        page = 0;
        render();
    }

    private void render() {
        inventoryGrid.getChildren().clear();

        String[] headers = {"Item name", "Category", "Qty", "Unit", "Status", "Last updated", "Actions"};
        for (int c = 0; c < headers.length; c++) {
            Label h = new Label(headers[c]);
            h.getStyleClass().add("adm-th");
            h.setMaxWidth(Double.MAX_VALUE);
            inventoryGrid.add(h, c, 0);
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, filtered.size());

        if (filtered.isEmpty()) {
            Label empty = new Label("No inventory items match your filters.");
            empty.getStyleClass().add("adm-td");
            empty.setMaxWidth(Double.MAX_VALUE);
            inventoryGrid.add(empty, 0, 1, headers.length, 1);
            showingLabel.setText("No items to show");
        } else {
            for (int i = from; i < to; i++) {
                addRow(i - from + 1, filtered.get(i));
            }
            showingLabel.setText("Showing " + (from + 1) + "–" + to + " of " + filtered.size() + " inventory items");
        }

        buildPagination();
    }

    private void addRow(int row, InventoryItem item) {
        inventoryGrid.add(cell(item.name()), 0, row);
        inventoryGrid.add(cell(item.category()), 1, row);
        inventoryGrid.add(cell(item.quantityText()), 2, row);
        inventoryGrid.add(cell(item.unit()), 3, row);

        Label pill = new Label(item.status().getDisplayName());
        pill.getStyleClass().add(item.status().getPillStyleClass());
        inventoryGrid.add(wrap(pill), 4, row);

        inventoryGrid.add(cell(item.lastUpdated().format(UPDATED)), 5, row);

        Button edit = new Button("Edit");
        edit.getStyleClass().add("adm-link");
        edit.setOnAction(e -> handleEdit(item));

        Button delete = new Button("🗑");
        delete.getStyleClass().add("adm-icon-btn");
        delete.setOnAction(e -> handleDelete(item));

        HBox actions = new HBox(12, edit, delete);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("adm-td");
        inventoryGrid.add(actions, 6, row);
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
    private void handleViewLowStock(ActionEvent event) {
        if (shell != null) shell.navigate(AdminPage.LOW_STOCK);
    }

    private void handleEdit(InventoryItem item) {
        if (shell != null) shell.navigate(AdminPage.INVENTORY_EDIT, item.id());
    }

    private void handleDelete(InventoryItem item) {
        boolean confirmed = AlertsUtil.confirm(inventoryGrid, AlertsUtil.Type.WARNING, "Delete this item?",
                "Delete " + item.name() + " from the inventory?\nThis can't be undone.", "Delete");
        if (!confirmed) return;

        Thread worker = new Thread(() -> {
            Throwable failure = null;
            try {
                inventoryDAO.delete(item.id());
            } catch (Throwable t) {
                failure = t;
            }

            final Throwable finalFailure = failure;
            Platform.runLater(() -> {
                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    AlertsUtil.error(inventoryGrid, "Delete failed",
                            "Could not delete the item: " + finalFailure.getMessage());
                } else {
                    loadItems();
                }
            });
        }, "delete-inventory-item");
        worker.setDaemon(true);
        worker.start();
    }
}