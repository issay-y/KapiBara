package com.cafe.kapibara.kapibara.controller.main.cashier.order;

import com.cafe.kapibara.kapibara.controller.main.cashier.CashierPage;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Line;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import com.cafe.kapibara.kapibara.controller.main.cashier.ProductCards;
import com.cafe.kapibara.kapibara.util.MessageBox;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.money;

/** "New order": pick products on the left, review and pay on the right. */
public class NewOrderController implements CashierShell.Aware {

    private static final int VISIBLE_PRODUCTS = 6;
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
    private String category = "Coffee";

    @FXML
    private void initialize() {
        searchField.textProperty().addListener((obs, old, text) -> renderProducts());
    }

    @Override
    public void attach(CashierShell shell) {
        this.shell = shell;
        renderChips();
        renderProducts();
        renderOrder();
    }

    // ------------------------------------------------------------------
    // Menu side
    // ------------------------------------------------------------------

    private void renderChips() {
        chipRow.getChildren().clear();
        for (String name : CashierStore.CATEGORIES) {
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
        List<Product> shown = matches.size() > VISIBLE_PRODUCTS ? matches.subList(0, VISIBLE_PRODUCTS) : matches;

        sectionLabel.setText(searching ? "Search results" : category);
        countLabel.setText(shown.size() + " of " + matches.size() + " products");

        productGrid.getChildren().clear();
        if (matches.isEmpty()) {
            Label none = ProductCards.label("No items match your search.", "adm-card-foot");
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
        store.remove(p);
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
        store.clear();
        renderProducts();
        renderOrder();
    }

    @FXML
    private void handleBrowse() {
        shell.navigate(CashierPage.BROWSE_MENU);
    }
}