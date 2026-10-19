package com.cafe.kapibara.kapibara.controller.main.cashier.menu;

import com.cafe.kapibara.kapibara.controller.main.cashier.CashierPage;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import com.cafe.kapibara.kapibara.controller.main.cashier.ProductCards;
import com.cafe.kapibara.kapibara.util.MessageBox;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** "Browse the menu": the whole menu in a grid, sharing the order with the New Order screen. */
public class BrowseMenuController implements CashierShell.Aware {

    private static final int COLUMNS = 4;

    @FXML private TextField searchField;
    @FXML private HBox chipRow;
    @FXML private GridPane productGrid;
    @FXML private Label footLabel;
    @FXML private Button returnBtn;

    private final CashierStore store = CashierStore.get();
    private CashierShell shell;
    private String category = null; // null = All

    @FXML
    private void initialize() {
        // The search box and the category chips work together: whatever is typed
        // stays in the box and filters inside whichever category is selected.
        searchField.textProperty().addListener((obs, old, text) -> render());
    }

    @Override
    public void attach(CashierShell shell) {
        this.shell = shell;
        renderChips();
        render();
    }

    private void renderChips() {
        chipRow.getChildren().clear();
        chipRow.getChildren().add(chip("All", null));
        CashierStore.CATEGORIES.forEach(name -> chipRow.getChildren().add(chip(name, name)));
    }

    private Button chip(String text, String value) {
        boolean active = value == null ? category == null : value.equals(category);
        return ProductCards.chip(text, active, () -> {
            category = value;          // keep the search text; just change the category
            renderChips();
            render();
        });
    }

    private void render() {
        List<Product> matches = store.search(category, searchField.getText());

        productGrid.getChildren().clear();
        if (matches.isEmpty()) {
            String where = category == null ? "" : " in " + category;
            productGrid.add(ProductCards.label("No items match your search" + where + ".", "adm-card-foot"),
                    0, 0, COLUMNS, 1);
        }
        int i = 0;
        for (Product p : matches) {
            VBox card = ProductCards.wide(p, store.qtyInCart(p), this::addToOrder);
            GridPane.setHgrow(card, Priority.ALWAYS);
            productGrid.add(card, i % COLUMNS, i / COLUMNS);
            i++;
        }

        footLabel.setText(matches.size() + " of " + store.products().size()
                + " items shown • Prices include VAT");

        int n = store.itemCount();
        returnBtn.setText(n == 0 ? "Return to order"
                : "Return to order · " + n + (n == 1 ? " item" : " items")
                + " · " + CashierStore.moneyShort(store.total()));
    }

    private void addToOrder(Product p) {
        if (!store.add(p)) {
            MessageBox.info(returnBtn, "Not enough stock",
                    "Only " + p.getStock() + " × " + p.getName()
                            + " available, and they're all in this order already.");
            return;
        }
        render();
    }

    @FXML
    private void handleReturn() {
        shell.navigate(CashierPage.NEW_ORDER);
    }
}