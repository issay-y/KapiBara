package com.cafe.kapibara.kapibara.controller.main.cashier.order;

import com.cafe.kapibara.kapibara.controller.main.cashier.CashierPage;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Line;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import com.cafe.kapibara.kapibara.controller.main.cashier.ProductCards;
import com.cafe.kapibara.kapibara.util.MessageBox;
import javafx.application.Platform;
import javafx.fxml.FXML;
import com.cafe.kapibara.kapibara.util.VoidGuard;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.money;

/** "New order": pick products on the left, review and pay on the right. */
public class NewOrderController implements CashierShell.Aware {

    private static final int COLUMNS = 3;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy • h:mm a", Locale.ENGLISH);

    @FXML private TextField searchField;
    @FXML private HBox chipRow;
    @FXML private Label sectionLabel, countLabel;
    @FXML private GridPane productGrid;

    @FXML private Label itemsPill, orderNoLabel, orderTimeLabel;
    @FXML private VBox linesBox;
    @FXML private Label subtotalLabel, vatCaption, vatLabel, discountCaption, discountLabel, totalLabel;
    @FXML private Button payBtn;

    private final CashierStore store = CashierStore.get();
    private CashierShell shell;
    private String category;
    private String loadError;

    @FXML
    private void initialize() {
        searchField.textProperty().addListener((obs, old, text) -> renderProducts());
    }

    @Override
    public void attach(CashierShell shell) {
        this.shell = shell;
        hideScrollBars();
        loadMenu();
        renderChips();
        renderProducts();
        renderOrder();
    }

    /** Reads the products from the database every time this page opens. */
    private void loadMenu() {
        loadError = null;
        try {
            store.refreshMenu();
        } catch (SQLException e) {
            loadError = e.getMessage();
        }
        ProductCards.clearImageCache();

        List<String> categories = store.categories();
        if (category != null && !categories.contains(category)) {
            category = null;   // the chosen category no longer exists: fall back to "All"
        }
    }

    /**
     * Makes the scroll bars on this page invisible while the lists still scroll
     * (mouse wheel, touch drag, keyboard). The CSS lives in Cashier.css (.csh-quiet).
     */
    private void hideScrollBars() {
        Runnable apply = () -> {
            if (productGrid.getScene() != null) {
                productGrid.getScene().getRoot().applyCss();   // makes sure the scroll panes have their skins
            }
            quiet(productGrid);
            quiet(linesBox);
        };
        productGrid.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) Platform.runLater(apply);
        });
        if (productGrid.getScene() != null) Platform.runLater(apply);
    }

    /** Marks the nearest scroll pane around the node. */
    private static void quiet(Node start) {
        for (Node n = start; n != null; n = n.getParent()) {
            if (n instanceof ScrollPane) {
                if (!n.getStyleClass().contains("csh-quiet")) n.getStyleClass().add("csh-quiet");
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Menu side
    // ------------------------------------------------------------------

    private void renderChips() {
        chipRow.getChildren().clear();
        chipRow.getChildren().add(ProductCards.chip("All", category == null, () -> {
            category = null;
            searchField.clear();
            renderChips();
            renderProducts();
        }));
        for (String name : store.categories()) {
            chipRow.getChildren().add(ProductCards.chip(name, name.equals(category), () -> {
                category = name;
                searchField.clear();
                renderChips();
                renderProducts();
            }));
        }
    }

    private void renderProducts() {
        String query = searchField.getText();
        boolean searching = query != null && !query.isBlank();

        List<Product> matches = store.search(category, query);
        List<Product> shown = matches;

        sectionLabel.setText(searching ? "Search results" : (category == null ? "All items" : category));
        countLabel.setText(matches.size() + (matches.size() == 1 ? " product" : " products"));

        productGrid.getChildren().clear();
        if (matches.isEmpty()) {
            String message = loadError != null
                    ? "Could not load the products: " + loadError
                    : searching ? "No items match your search."
                    : "No products to sell yet. Set a price on your inventory items to show them here.";
            Label none = ProductCards.label(message, "adm-card-foot");
            none.setWrapText(true);
            productGrid.add(none, 0, 0, COLUMNS, 1);
            return;
        }

        int i = 0;
        for (Product p : shown) {
            VBox card = ProductCards.compact(p, store.qtyInCart(p), this::addToOrder);
            GridPane.setHgrow(card, Priority.ALWAYS);
            productGrid.add(card, i % COLUMNS, i / COLUMNS);
            i++;
        }
    }

    private void addToOrder(Product p) {
        if (!store.add(p)) {
            MessageBox.info(payBtn, "Not enough stock",
                    "Only " + p.getStock() + " × " + p.getName()
                            + " available, and they're all in this order already.");
            return;
        }
        renderProducts();
        renderOrder();
    }

    // ------------------------------------------------------------------
    // Order side
    // ------------------------------------------------------------------

    private void renderOrder() {
        int n = store.itemCount();
        itemsPill.setText(n == 1 ? "1 item" : n + " items");
        orderNoLabel.setText("Order #" + store.currentOrderNumber());
        orderTimeLabel.setText(store.orderTime().format(STAMP));

        linesBox.getChildren().clear();
        List<Line> lines = store.lines();
        if (lines.isEmpty()) {
            Label empty = ProductCards.label("No items yet. Tap Add on a product to start the order.", "adm-card-foot");
            empty.setWrapText(true);
            linesBox.getChildren().add(empty);
        } else {
            lines.forEach(line -> linesBox.getChildren().add(buildLine(line)));
        }

        boolean pwd = store.isSeniorPwd();
        subtotalLabel.setText(money(store.subtotal()));
        vatCaption.setText(pwd ? "VAT (exempt)" : "VAT (12%, included)");
        vatLabel.setText(money(store.vat()));
        discountCaption.setText(pwd ? "Discount (PWD / Senior 20%)" : "Discount");
        discountLabel.setText(pwd ? "−" + money(store.discount()) : money(store.discount()));
        totalLabel.setText(money(store.total()));
    }

    private VBox buildLine(Line line) {
        Product p = line.getProduct();

        HBox top = new HBox(ProductCards.label(p.getName(), "csh-line-name"), ProductCards.spacer(),
                ProductCards.label(money(line.lineTotal()), "csh-line-price"));
        top.setAlignment(Pos.CENTER_LEFT);

        Label each = ProductCards.label(money(p.getPrice()) + " each", "csh-line-each");

        Button minus = stepper("−", () -> {
            store.decrease(p);
            renderOrder();
            renderProducts();
        });
        minus.setDisable(line.getQty() <= 1);

        Label qty = ProductCards.label(String.valueOf(line.getQty()), "csh-qty");
        qty.setMinWidth(40);
        qty.setAlignment(Pos.CENTER);

        Button plus = stepper("+", () -> addToOrder(p));

        Button trash = new Button("🗑");
        trash.getStyleClass().add("adm-icon-btn");
        trash.setFocusTraversable(false);
        trash.setOnAction(e -> removeLine(p));

        HBox controls = new HBox(6, minus, qty, plus, ProductCards.spacer(), trash);
        controls.setAlignment(Pos.CENTER_LEFT);

        return new VBox(6, top, each, controls);
    }

    private Button stepper(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("csh-stepper-btn");
        b.setFocusTraversable(false);
        b.setOnAction(e -> action.run());
        return b;
    }

    private void removeLine(Product p) {
        if (!MessageBox.confirm(payBtn, "Remove item?",
                "Remove " + p.getName() + " from this order?", "Remove", "Keep it", true)) {
            return;
        }
        if (!VoidGuard.authorize(payBtn)) return;   // PIN check
        store.remove(p);
        renderProducts();
        renderOrder();
    }

    @FXML
    private void handleClear() {
        if (store.isEmpty()) {
            MessageBox.info(payBtn, "Nothing to clear", "The order is already empty.");
            return;
        }
        int n = store.itemCount();
        if (!MessageBox.confirm(payBtn, "Clear order?",
                "All " + n + (n == 1 ? " item" : " items") + " will be removed from this order.",
                "Clear order", "Keep order", true)) {
            return;
        }
        if (!VoidGuard.authorize(payBtn)) return;   // PIN check
        store.clear();
        renderProducts();
        renderOrder();
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    @FXML
    private void handlePay() {
        if (store.isEmpty()) {
            MessageBox.info(payBtn, "The order is empty", "Add at least one item before taking payment.");
            return;
        }
        shell.navigate(CashierPage.CHECKOUT);
    }


    @FXML
    private void handleBrowse() {
        shell.navigate(CashierPage.BROWSE_MENU);
    }
}