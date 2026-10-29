package com.cafe.kapibara.kapibara.controller.main.admin.inventory;

import com.cafe.kapibara.kapibara.DAO.InventoryDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.model.inventory.InventoryItem;
import com.cafe.kapibara.kapibara.model.inventory.StockStatus;
import com.cafe.kapibara.kapibara.util.TableRowUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TreeSet;

public class LowStockController implements Initializable, AdminShell.Aware {

    private static final String ALL_CATEGORIES = "All categories";
    private static final String ALL_STATUSES = "All statuses";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    // KPI cards
    // (criticalValue / criticalSub keep their fx:id but now show the OUT OF STOCK card)
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

    // added in code (no FXML change needed)
    private final ComboBox<String> statusCombo = new ComboBox<>();
    private final Button copyBtn = new Button("Copy restock list");
    private boolean extrasMounted = false;

    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final List<InventoryItem> attentionItems = new ArrayList<>();
    private List<InventoryItem> shownRows = List.of();
    private AdminShell shell;
    private String reviewDate = "";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        categoryCombo.getItems().add(ALL_CATEGORIES);
        categoryCombo.getSelectionModel().selectFirst();

        statusCombo.getItems().setAll(ALL_STATUSES, StockStatus.OUT_OF_STOCK.getDisplayName(),
                StockStatus.LOW.getDisplayName());
        statusCombo.setValue(ALL_STATUSES);

        searchField.textProperty().addListener((obs, old, now) -> render());
        categoryCombo.valueProperty().addListener((obs, old, now) -> render());
        statusCombo.valueProperty().addListener((obs, old, now) -> render());

        lowStockGrid.getStyleClass().add("inv-grid");   // compact rows (same CSS as the Inventory page)

        loadItems();
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;
        mountExtras();
        refreshReviewSub();
    }

    /** Status filter, "Copy restock list" button and brown outlines, added without touching the FXML. */
    private void mountExtras() {
        if (extrasMounted) return;
        extrasMounted = true;

        for (Node n = lowStockGrid; n != null; n = n.getParent()) {
            if (n.getStyleClass().contains("adm-lg") || n.getStyleClass().contains("adm-page")) {
                if (!n.getStyleClass().contains("adm-tx")) n.getStyleClass().add("adm-tx");
                break;
            }
        }

        statusCombo.getStyleClass().add("adm-combo");
        statusCombo.setPrefWidth(categoryCombo.getPrefWidth() > 0 ? categoryCombo.getPrefWidth() : 220);

        copyBtn.getStyleClass().add("adm-outline-btn");
        copyBtn.setTooltip(new Tooltip("Copy the items below to the clipboard"));
        copyBtn.setOnAction(e -> copyRestockList());

        if (categoryCombo.getParent() instanceof Pane row) {
            int at = row.getChildren().indexOf(categoryCombo);
            row.getChildren().add(at + 1, statusCombo);
            row.getChildren().add(at + 2, copyBtn);
        } else {
            System.err.println("LowStockController: could not find the filter row.");
        }
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
        int low = countStatus(StockStatus.LOW);

        needsValue.setText(attentionItems.size() + (attentionItems.size() == 1 ? " item" : " items"));
        needsSub.setText(low + " low stock + " + out + " out of stock");

        // Second card: out of stock items (first two names, then "and N more")
        List<String> outNames = new ArrayList<>();
        for (InventoryItem item : attentionItems) {
            if (item.status() == StockStatus.OUT_OF_STOCK) outNames.add(titleCase(item.name()));
        }
        criticalValue.setText(outNames.size() + (outNames.size() == 1 ? " item" : " items"));
        criticalSub.setText(outNames.isEmpty() ? "Nothing is unavailable" : summary(outNames, 2));

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
        List<String> lowNames = new ArrayList<>();
        for (InventoryItem item : attentionItems) {
            switch (item.status()) {
                case OUT_OF_STOCK -> outNames.add(titleCase(item.name()));
                case LOW -> lowNames.add(titleCase(item.name()));
                default -> { }
            }
        }

        alertBanner.setVisible(true);
        alertBanner.setManaged(true);

        if (!outNames.isEmpty()) {
            alertTitle.setText("Restock unavailable items first");
            String text = outNames.size() + " unavailable: " + summary(outNames, 3) + ".";
            if (!lowNames.isEmpty()) text += " Also running low: " + summary(lowNames, 3) + ".";
            alertSub.setText(text);
        } else if (!lowNames.isEmpty()) {
            alertTitle.setText("Top up low items soon");
            alertSub.setText("Running low: " + summary(lowNames, 4) + ".");
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

    /** "A, B and C", or "A, B and 7 more" when the list is longer than show + 1. */
    private static String summary(List<String> names, int show) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        if (names.size() <= show + 1) {
            return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
        }
        return String.join(", ", names.subList(0, show)) + " and " + (names.size() - show) + " more";
    }

    /** "BLUEBERRY MUFFIN" -> "Blueberry Muffin" (display only). */
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
    //  TABLE
    // =====================================================================

    private void render() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String category = categoryCombo.getValue();
        String status = statusCombo.getValue();

        List<InventoryItem> rows = attentionItems.stream()
                .filter(i -> q.isEmpty() || i.name().toLowerCase(Locale.ROOT).contains(q))
                .filter(i -> category == null || ALL_CATEGORIES.equals(category) || i.category().equals(category))
                .filter(i -> status == null || ALL_STATUSES.equals(status)
                        || i.status().getDisplayName().equals(status))
                .toList();
        shownRows = rows;

        lowStockGrid.getChildren().clear();

        String[] headers = {"Item name", "Category", "Stock level", "To reach minimum", "Status", "Action"};
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
            TableRowUtil.polish(lowStockGrid, headers.length);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            addRow(i + 1, rows.get(i));
        }
        TableRowUtil.polish(lowStockGrid, headers.length);   // stretch cells + straight row lines
    }

    private void addRow(int row, InventoryItem item) {
        boolean alt = row % 2 == 0;   // every other row slightly darker

        lowStockGrid.add(tint(cell(titleCase(item.name())), alt), 0, row);
        lowStockGrid.add(tint(cell(item.category()), alt), 1, row);

        // stock level: "5 / 10 pcs" with a bar
        Label level = new Label(item.quantityText() + " / " + item.minimumText() + " " + item.unit());
        level.getStyleClass().add("adm-stock-value");
        double ratio = item.minimum() > 0 ? Math.min(1.0, item.quantity() / item.minimum()) : 0;
        ProgressBar bar = new ProgressBar(ratio);
        bar.getStyleClass().add("adm-seller-bar");
        bar.setPrefWidth(150);
        bar.setMaxWidth(150);
        VBox levelBox = new VBox(4, level, bar);
        levelBox.setAlignment(Pos.CENTER_LEFT);
        levelBox.getStyleClass().add("adm-td");
        lowStockGrid.add(tint(levelBox, alt), 2, row);

        // how much is missing to get back to the minimum
        double need = item.minimum() - item.quantity();
        Label needLabel = new Label(need > 0 ? "+" + number(need) + " " + item.unit() : "At minimum");
        needLabel.getStyleClass().addAll("adm-td", need > 0 ? "ls-need" : "ls-ok");
        needLabel.setMaxWidth(Double.MAX_VALUE);
        lowStockGrid.add(tint(needLabel, alt), 3, row);

        Label pill = new Label(item.status().getDisplayName());
        pill.getStyleClass().add(item.status().getPillStyleClass());
        HBox pillCell = new HBox(pill);
        pillCell.setAlignment(Pos.CENTER_LEFT);
        pillCell.getStyleClass().add("adm-td");
        lowStockGrid.add(tint(pillCell, alt), 4, row);

        Button update = new Button();
        update.setGraphic(pencilIcon());
        update.getStyleClass().add("tx-icon-btn");
        update.setTooltip(new Tooltip("Update quantity"));
        update.setOnAction(e -> handleUpdateQuantity(item));
        HBox actionCell = new HBox(update);
        actionCell.setAlignment(Pos.CENTER_LEFT);
        actionCell.getStyleClass().add("adm-td");
        lowStockGrid.add(tint(actionCell, alt), 5, row);
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

    private static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.1f", v);
    }

    private static SVGPath pencilIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M0 12 L0.6 8.6 L8.6 0.6 C9.1 0.1 9.9 0.1 10.4 0.6 L11.4 1.6 C11.9 2.1 11.9 2.9 11.4 3.4 "
                + "L3.4 11.4 Z");
        p.getStyleClass().add("tx-icon");
        return p;
    }

    // =====================================================================
    //  COPY RESTOCK LIST
    // =====================================================================

    private void copyRestockList() {
        if (shownRows.isEmpty()) return;

        StringBuilder sb = new StringBuilder("Restock list · ").append(LocalDate.now().format(TODAY)).append("\n");
        for (InventoryItem item : shownRows) {
            double need = item.minimum() - item.quantity();
            sb.append("- ").append(titleCase(item.name())).append(": ")
                    .append(item.quantityText()).append(" / ").append(item.minimumText())
                    .append(" ").append(item.unit());
            if (need > 0) sb.append(" (need ").append(number(need)).append(")");
            sb.append("\n");
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString().trim());
        Clipboard.getSystemClipboard().setContent(content);

        copyBtn.setText("Copied ✓");
        PauseTransition reset = new PauseTransition(Duration.seconds(2));
        reset.setOnFinished(e -> copyBtn.setText("Copy restock list"));
        reset.play();
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================

    @FXML
    private void handleBack(ActionEvent event) {
        if (shell != null) shell.navigate(AdminPage.INVENTORY);
    }

    private void handleUpdateQuantity(InventoryItem item) {
        if (shell != null) shell.navigate(AdminPage.INVENTORY_EDIT, item.id());
    }
}