package com.cafe.kapibara.kapibara.controller.main.cashier.home;

import com.cafe.kapibara.kapibara.controller.main.cashier.CashierPage;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import com.cafe.kapibara.kapibara.util.MessageBox;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Cashier "Home": shift banner, shift totals, recent sales and counter notes. */
public class CashierHomeController implements CashierShell.Aware {

    private static final DateTimeFormatter HERO_DATE = DateTimeFormatter.ofPattern("EEEE, dd MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    @FXML private Label greetingTitle, greetingSub, heroDate, heroShift;
    @FXML private Label salesValue, salesSub, txValue, txSub, avgValue, avgSub, orderValue, orderSub;
    @FXML private GridPane txGrid;
    @FXML private Label txEmptyLabel;
    @FXML private HBox noteBanner;
    @FXML private Label noteTitle, noteSub, allGoodLabel;

    private final CashierStore store = CashierStore.get();
    private CashierShell shell;

    @Override
    public void attach(CashierShell shell) {
        this.shell = shell;

        try {
            CashierStore.get().refreshSalesHistory();
        } catch (java.sql.SQLException e) {
            System.err.println("Could not load transactions from database: " + e.getMessage());
        }

        render();
    }

    private void render() {
        greetingTitle.setText(CashierShell.greeting() + ", "
                + CashierShell.firstName(shell.getCurrentUserName()) + ".");
        greetingSub.setText("You're on the " + CashierStore.SHIFT_NAME.toLowerCase(Locale.ENGLISH)
                + " shift. Here's your counter at a glance.");

        heroDate.setText(LocalDate.now().format(HERO_DATE).toUpperCase(Locale.ENGLISH));
        heroShift.setText(CashierStore.SHIFT_NAME + " shift • " + CashierStore.SHIFT_HOURS
                + " • " + CashierStore.COUNTER);

        int count = store.salesCount();
        salesValue.setText(CashierStore.moneyShort(store.shiftSales()));
        salesSub.setText(count == 1 ? "1 cash transaction completed" : count + " cash transactions completed");

        txValue.setText(String.valueOf(count));
        txSub.setText(store.latestSaleTime()
                .map(t -> "Latest order at " + t.format(TIME))
                .orElse("No orders yet"));

        avgValue.setText(CashierStore.moneyShort(store.averageSale()));
        avgSub.setText("Across your completed orders");

        int items = store.itemCount();
        orderValue.setText(CashierStore.moneyShort(store.total()));
        orderSub.setText(items == 0 ? "No items yet"
                : items + (items == 1 ? " item" : " items") + " · Awaiting payment");

        renderTransactions();
        renderNotes();
    }

    private void renderTransactions() {
        txGrid.getChildren().clear();

        String[] heads = {"Transaction", "Time", "Items", "Total", "Status"};
        for (int c = 0; c < heads.length; c++) {
            txGrid.add(cell(heads[c], "adm-th"), c, 0);
        }

        List<Sale> recent = store.recentSales(4);
        boolean empty = recent.isEmpty();
        txEmptyLabel.setVisible(empty);
        txEmptyLabel.setManaged(empty);

        int row = 1;
        for (Sale sale : recent) {
            txGrid.add(cell(sale.number(), "adm-td"), 0, row);
            txGrid.add(cell(sale.time().format(TIME), "adm-td"), 1, row);
            txGrid.add(cell(String.valueOf(sale.items()), "adm-td"), 2, row);
            txGrid.add(cell(CashierStore.money(sale.total()), "adm-td"), 3, row);

            Label pill = new Label("Paid");
            pill.getStyleClass().add("adm-pill-green");
            HBox statusCell = new HBox(pill);
            statusCell.setAlignment(Pos.CENTER_LEFT);
            statusCell.getStyleClass().add("adm-td");
            statusCell.setMaxWidth(Double.MAX_VALUE);
            txGrid.add(statusCell, 4, row);
            row++;
        }
    }

    private void renderNotes() {
        Optional<Product> out = store.firstOutOfStock();
        boolean any = out.isPresent();

        noteBanner.setVisible(any);
        noteBanner.setManaged(any);
        allGoodLabel.setVisible(!any);
        allGoodLabel.setManaged(!any);

        if (any) {
            long others = store.outOfStockCount() - 1;
            noteTitle.setText(out.get().getName() + " is unavailable");
            noteSub.setText(others > 0
                    ? "Out of stock, along with " + others + " other " + (others == 1 ? "item" : "items")
                    + ". Offer an alternative from the menu."
                    : "Out of stock. Offer an alternative from the menu.");
        }
    }

    private static Node cell(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        l.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(l, Priority.ALWAYS);
        return l;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    @FXML
    private void handleViewAll() {
        MessageBox.comingSoon(txGrid, "Transaction history");
    }

    @FXML
    private void handleOpenOrder() {
        shell.navigate(CashierPage.NEW_ORDER);
    }
}