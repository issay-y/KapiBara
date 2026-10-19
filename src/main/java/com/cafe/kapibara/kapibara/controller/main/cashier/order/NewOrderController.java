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
import javafx.concurrent.Task;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

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

    private RotateTransition spin;
    private long loadStartedAt;
    private static final long MIN_LOADING_MS = 600;

    @FXML
    private void initialize() {
        searchField.textProperty().addListener((obs, old, text) -> renderProducts());
    }

    @Override
    public void attach(CashierShell shell) {
        this.shell = shell;
        hideScrollBars();
        loadMenuAsync();
    }

    private void loadMenuAsync() {
        showLoading();
        loadStartedAt = System.currentTimeMillis();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                store.refreshMenu();
                return null;
            }
        };
        task.setOnSucceeded(e -> finishAfterMinimum(null));
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            finishAfterMinimum(ex == null ? "Unknown error" : ex.getMessage());
        });

        Thread worker = new Thread(task, "load-menu");
        worker.setDaemon(true);
        worker.start();
    }

    /** Keeps the spinner on screen long enough to be seen, even if the database answers instantly. */
    private void finishAfterMinimum(String error) {
        long elapsed = System.currentTimeMillis() - loadStartedAt;
        long wait = Math.max(0, MIN_LOADING_MS - elapsed);
        PauseTransition pause = new PauseTransition(Duration.millis(wait));
        pause.setOnFinished(e -> finishLoading(error));
        pause.play();
    }

    private void showLoading() {
        payBtn.setDisable(true);
        chipRow.getChildren().clear();
        sectionLabel.setText("Loading menu…");
        countLabel.setText("");

        Label text = ProductCards.label("Loading products…", "csh-loading-text");

        VBox box = new VBox(16, buildSpinner(), text);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new javafx.geometry.Insets(80, 0, 80, 0));

        productGrid.getChildren().clear();
        productGrid.add(box, 0, 0, COLUMNS, 1);
        GridPane.setHgrow(box, Priority.ALWAYS);
    }

    /** A ring with an orange arc that rotates forever (does not depend on the JavaFX skin). */
    private StackPane buildSpinner() {
        Circle track = new Circle(0, 0, 24);
        track.setFill(Color.TRANSPARENT);
        track.setStroke(Color.web("#EBDDCB"));
        track.setStrokeWidth(5);

        Arc arc = new Arc(0, 0, 24, 24, 90, 110);
        arc.setType(ArcType.OPEN);
        arc.setFill(Color.TRANSPARENT);
        arc.setStroke(Color.web("#FF8732"));
        arc.setStrokeWidth(5);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);

        // Both shapes share the same centre, so the group rotates around the ring's middle
        Group ring = new Group(track, arc);

        if (spin != null) spin.stop();
        spin = new RotateTransition(Duration.millis(900), ring);
        spin.setByAngle(360);
        spin.setInterpolator(Interpolator.LINEAR);
        spin.setCycleCount(Animation.INDEFINITE);
        spin.play();

        StackPane holder = new StackPane(ring);
        holder.setMinSize(64, 64);
        holder.setPrefSize(64, 64);
        holder.setMaxSize(64, 64);
        return holder;
    }

    private void finishLoading(String error) {
        if (spin != null) {          // stop the animation so it doesn't keep running in the background
            spin.stop();
            spin = null;
        }

        loadError = error;
        ProductCards.clearImageCache();

        List<String> categories = store.categories();
        if (category != null && !categories.contains(category)) {
            category = null;
        }

        payBtn.setDisable(false);
        renderChips();
        renderProducts();
        renderOrder();
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