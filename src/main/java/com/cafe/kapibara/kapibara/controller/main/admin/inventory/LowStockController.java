package com.cafe.kapibara.kapibara.controller.main.admin.inventory;

import com.cafe.kapibara.kapibara.DAO.InventoryDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.model.inventory.InventoryItem;
import com.cafe.kapibara.kapibara.model.inventory.StockStatus;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TreeSet;

/** Everything at or below its minimum, most urgent first. */
public class LowStockController implements Initializable, AdminShell.Aware {

    private static final String ALL_CATEGORIES = "All categories";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    // KPI cards
    @FXML private Label needsValue, needsSub;
    @FXML private Label criticalValue, criticalSub;
    @FXML private Label reviewValue, reviewSub;

    // banner
    @FXML private HBox alertBanner;
    @FXML private Label alertTitle, alertSub;

    // filters
    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryCombo;

    // table
    @FXML private GridPane lowStockGrid;

    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final List<InventoryItem> attentionItems = new ArrayList<>();
    private AdminShell shell;
    private String reviewDate = "";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        categoryCombo.getItems().add(ALL_CATEGORIES);
        categoryCombo.getSelectionModel().selectFirst();

        searchField.textProperty().addListener((obs, old, now) -> render());
        categoryCombo.valueProperty().addListener((obs, old, now) -> render());

        loadItems();
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;
        refreshReviewSub();
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

                attentionItems.clear();
                finalItems.stream()
                        .filter(i -> i.status() != StockStatus.IN_STOCK)
                        .sorted(Comparator
                                .comparingInt((InventoryItem i) -> i.status().getSeverity())
                                .thenComparingDouble(i -> i.minimum() == 0 ? 0 : i.quantity() / i.minimum())
                                .thenComparing(InventoryItem::name))
                        .forEach(attentionItems::add);

                loadKpis(finalItems);
                loadBanner();
                refreshCategories();
                render();
            });
        }, "load-low-stock");
        worker.setDaemon(true);
        worker.start();
    }

    private void showLoadError() {
        lowStockGrid.getChildren().clear();
        Label error = new Label("Could not load inventory. Please make sure the database is running.");
        error.getStyleClass().add("adm-td");
        error.setMaxWidth(Double.MAX_VALUE);
        lowStockGrid.add(error, 0, 0, 6, 1);
    }

    private void refreshCategories() {
        String selected = categoryCombo.getValue();

        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (InventoryItem item : attentionItems) categories.add(item.category());

        List<String> options = new ArrayList<>();
        options.add(ALL_CATEGORIES);
        options.addAll(categories);
        categoryCombo.getItems().setAll(options);

        categoryCombo.setValue(selected != null && options.contains(selected) ? selected : ALL_CATEGORIES);
    }

    // =====================================================================
    //  KPIs + BANNER
    // =====================================================================

    private void loadKpis(List<InventoryItem> all) {
        int out = countStatus(StockStatus.OUT_OF_STOCK);
        int critical = countStatus(StockStatus.CRITICAL);
        int low = countStatus(StockStatus.LOW) + critical;

        needsValue.setText(attentionItems.size() + (attentionItems.size() == 1 ? " item" : " items"));
        needsSub.setText(low + " low stock + " + out + " out of stock");

        List<String> urgent = new ArrayList<>();
        for (InventoryItem item : attentionItems) {
            if (item.status().getSeverity() <= StockStatus.CRITICAL.getSeverity()) urgent.add(item.name());
        }
        criticalValue.setText(urgent.size() + (urgent.size() == 1 ? " item" : " items"));
        criticalSub.setText(join(urgent, " & "));

        Optional<LocalDateTime> latest = all.stream().map(InventoryItem::lastUpdated)
                .max(Comparator.naturalOrder());
        reviewValue.setText(latest.map(t -> t.format(TIME)).orElse("—"));
        reviewDate = latest.map(t -> t.format(DATE)).orElse("");
        refreshReviewSub();
    }

    private void refreshReviewSub() {
        if (shell == null || reviewDate.isEmpty()) {
            reviewSub.setText(reviewDate);
        } else {
            reviewSub.setText(reviewDate + " · " + shell.getCurrentUserName());
        }
    }

    private void loadBanner() {
        List<String> outNames = new ArrayList<>();
        List<String> criticalDetails = new ArrayList<>();
        List<String> lowNames = new ArrayList<>();
        for (InventoryItem item : attentionItems) {
            switch (item.status()) {
                case OUT_OF_STOCK -> outNames.add(item.name());
                case CRITICAL -> criticalDetails.add(item.name() + " (" + item.quantityText()
                        + " of " + item.minimumText() + " minimum)");
                case LOW -> lowNames.add(item.name());
                default -> { }
            }
        }

        alertBanner.setVisible(true);
        alertBanner.setManaged(true);

        String critical = criticalDetails.isEmpty() ? "" : "Critically low: " + join(criticalDetails, " and ") + ".";

        if (!outNames.isEmpty()) {
            alertTitle.setText("Restock unavailable items first");
            alertSub.setText((join(outNames, " and ") + (outNames.size() == 1 ? " is" : " are")
                    + " unavailable. " + critical).trim());
        } else if (!criticalDetails.isEmpty()) {
            alertTitle.setText("Restock critical items first");
            alertSub.setText(critical);
        } else if (!lowNames.isEmpty()) {
            alertTitle.setText("Top up low items soon");
            alertSub.setText("Running low: " + join(lowNames, " and ") + ".");
        } else {
            alertBanner.setVisible(false);
            alertBanner.setManaged(false);
        }
    }

    private int countStatus(StockStatus status) {
        int n = 0;
        for (InventoryItem item : attentionItems) {
            if (item.status() == status) n++;
        }
        return n;
    }

    private static String join(List<String> names, String lastSeparator) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + lastSeparator + names.get(names.size() - 1);
    }

    // =====================================================================
    //  TABLE
    // =====================================================================

    private void render() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String category = categoryCombo.getValue();

        List<InventoryItem> rows = attentionItems.stream()
                .filter(i -> q.isEmpty() || i.name().toLowerCase(Locale.ROOT).contains(q))
                .filter(i -> category == null || ALL_CATEGORIES.equals(category) || i.category().equals(category))
                .toList();

        lowStockGrid.getChildren().clear();

        String[] headers = {"Item name", "Current", "Minimum", "Unit", "Status", "Action"};
        for (int c = 0; c < headers.length; c++) {
            Label h = new Label(headers[c]);
            h.getStyleClass().add("adm-th");
            h.setMaxWidth(Double.MAX_VALUE);
            lowStockGrid.add(h, c, 0);
        }

        if (rows.isEmpty()) {
            Label empty = new Label(attentionItems.isEmpty()
                    ? "Nothing is running low right now."
                    : "No low-stock items match your filters.");
            empty.getStyleClass().add("adm-td");
            empty.setMaxWidth(Double.MAX_VALUE);
            lowStockGrid.add(empty, 0, 1, headers.length, 1);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            addRow(i + 1, rows.get(i));
        }
    }

    private void addRow(int row, InventoryItem item) {
        lowStockGrid.add(cell(item.name()), 0, row);
        lowStockGrid.add(cell(item.quantityText()), 1, row);
        lowStockGrid.add(cell(item.minimumText()), 2, row);
        lowStockGrid.add(cell(item.unit()), 3, row);

        Label pill = new Label(item.status().getDisplayName());
        pill.getStyleClass().add(item.status().getPillStyleClass());
        HBox pillCell = new HBox(pill);
        pillCell.setAlignment(Pos.CENTER_LEFT);
        pillCell.getStyleClass().add("adm-td");
        lowStockGrid.add(pillCell, 4, row);

        Button update = new Button("Update quantity →");
        update.getStyleClass().add("adm-link");
        update.setOnAction(e -> handleUpdateQuantity(item));
        HBox actionCell = new HBox(update);
        actionCell.setAlignment(Pos.CENTER_LEFT);
        actionCell.getStyleClass().add("adm-td");
        lowStockGrid.add(actionCell, 5, row);
    }

    private static Label cell(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("adm-td");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================

    @FXML
    private void handleBack(ActionEvent event) {
        if (shell != null) shell.navigate(AdminPage.INVENTORY);
    }

    /** Opens the edit form, where the quantity can be changed. */
    private void handleUpdateQuantity(InventoryItem item) {
        if (shell != null) shell.navigate(AdminPage.INVENTORY_EDIT, item.id());
    }
}