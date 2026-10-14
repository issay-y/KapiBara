package com.cafe.kapibara.kapibara.controller.main.cashier.transactions;

import com.cafe.kapibara.kapibara.controller.main.cashier.CashierPage;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import static com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.money;

/** "Transactions": every completed counter order, with search, filters, sorting and pages. */
public class TransactionsController implements CashierShell.Aware {

    private static final int PAGE_SIZE = 7;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final int COLUMNS = 8;

    private static final String D_TODAY = "Today";
    private static final String D_YESTERDAY = "Yesterday";
    private static final String D_WEEK = "Last 7 days";
    private static final String D_ALL = "All dates";
    private static final String P_ALL = "All payment statuses";
    private static final String P_PAID = "Paid";
    private static final String S_ALL = "All transactions";
    private static final String S_COMPLETE = "Complete";

    @FXML private TextField searchField;
    @FXML private ComboBox<String> dateCombo, paymentCombo, statusCombo;
    @FXML private Button sortBtn;
    @FXML private GridPane txGrid;
    @FXML private Label showingLabel, todayLabel;
    @FXML private HBox paginationBox;

    private final CashierStore store = CashierStore.get();
    private CashierShell shell;
    private int page = 1;
    private boolean newestFirst = true;

    @FXML
    private void initialize() {
        dateCombo.getItems().setAll(D_TODAY, D_YESTERDAY, D_WEEK, D_ALL);
        paymentCombo.getItems().setAll(P_ALL, P_PAID);
        statusCombo.getItems().setAll(S_ALL, S_COMPLETE);
        dateCombo.getSelectionModel().selectFirst();
        paymentCombo.getSelectionModel().selectFirst();
        statusCombo.getSelectionModel().selectFirst();

        searchField.textProperty().addListener((obs, old, text) -> resetAndRender());
        dateCombo.valueProperty().addListener((obs, old, v) -> resetAndRender());
        paymentCombo.valueProperty().addListener((obs, old, v) -> resetAndRender());
        statusCombo.valueProperty().addListener((obs, old, v) -> resetAndRender());
    }

    @Override
    public void attach(CashierShell shell) {
        this.shell = shell;
        render();
    }

    private void resetAndRender() {
        page = 1;
        render();
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private List<Sale> filtered() {
        LocalDate today = LocalDate.now();
        String date = dateCombo.getValue();
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);

        // Every sale in this build is a completed cash payment, so the Payment and Status
        // filters keep everything for now. Add real checks here when voids/refunds exist.
        List<Sale> list = new ArrayList<>(store.sales());
        list.removeIf(s -> !matchesDate(s, date, today));
        if (!query.isEmpty()) {
            list.removeIf(s -> !s.number().toLowerCase(Locale.ROOT).contains(query)
                    && !s.cashier().toLowerCase(Locale.ROOT).contains(query));
        }

        list.sort(Comparator.comparing(Sale::time));
        if (newestFirst) java.util.Collections.reverse(list);
        return list;
    }

    private static boolean matchesDate(Sale sale, String option, LocalDate today) {
        LocalDate day = sale.time().toLocalDate();
        return switch (option == null ? D_TODAY : option) {
            case D_YESTERDAY -> day.equals(today.minusDays(1));
            case D_WEEK -> !day.isBefore(today.minusDays(6)) && !day.isAfter(today);
            case D_ALL -> true;
            default -> day.equals(today);
        };
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void render() {
        List<Sale> list = filtered();
        int total = list.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        page = Math.min(Math.max(page, 1), pages);

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);
        List<Sale> rows = list.subList(from, to);

        txGrid.getChildren().clear();
        String[] heads = {"Transaction no.", "Time", "Cashier", "Items", "Total", "Payment", "Status", "Action"};
        for (int c = 0; c < heads.length; c++) {
            txGrid.add(textCell(heads[c], "adm-th"), c, 0);
        }

        if (rows.isEmpty()) {
            String message = store.sales().isEmpty()
                    ? "No transactions yet. Completed orders will appear here."
                    : "No transactions match your filters.";
            txGrid.add(textCell(message, "adm-td"), 0, 1, COLUMNS, 1);
        }

        int row = 1;
        for (Sale sale : rows) {
            txGrid.add(textCell(sale.number(), "adm-td"), 0, row);
            txGrid.add(textCell(sale.time().format(TIME), "adm-td"), 1, row);
            txGrid.add(textCell(sale.cashier(), "adm-td"), 2, row);
            txGrid.add(textCell(String.valueOf(sale.items()), "adm-td"), 3, row);
            txGrid.add(textCell(money(sale.total()), "adm-td"), 4, row);
            txGrid.add(pillCell("Paid"), 5, row);
            txGrid.add(pillCell("Complete"), 6, row);
            txGrid.add(viewCell(sale), 7, row);
            row++;
        }

        showingLabel.setText(total == 0
                ? "Showing 0 transactions"
                : "Showing " + (from + 1) + "–" + to + " of " + total + " transactions");
        todayLabel.setText("Today's completed sales: " + money(store.todaySalesTotal()));
        renderPagination(pages);
    }

    private static Label textCell(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        l.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(l, Priority.ALWAYS);
        return l;
    }

    private static HBox boxCell(Node content) {
        HBox cell = new HBox(content);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.getStyleClass().add("adm-td");
        cell.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }

    private static HBox pillCell(String text) {
        Label pill = new Label(text);
        pill.getStyleClass().add("adm-pill-green");
        return boxCell(pill);
    }

    private HBox viewCell(Sale sale) {
        Button view = new Button("View →");
        view.getStyleClass().add("adm-link");
        view.setFocusTraversable(false);
        view.setOnAction(e -> {
            store.setViewedSale(sale);
            shell.navigate(CashierPage.TRANSACTION_DETAIL);
        });
        return boxCell(view);
    }

    // ------------------------------------------------------------------
    // Pagination: Previous  1  2  3 …  Next
    // ------------------------------------------------------------------

    private void renderPagination(int pages) {
        paginationBox.getChildren().clear();
        paginationBox.setAlignment(Pos.CENTER_RIGHT);

        paginationBox.getChildren().add(navButton("Previous", page > 1, () -> goTo(page - 1)));

        TreeSet<Integer> shown = new TreeSet<>();
        for (int n : new int[] {1, 2, 3, pages, page - 1, page, page + 1}) {
            if (n >= 1 && n <= pages) shown.add(n);
        }
        int last = 0;
        for (int n : shown) {
            if (n - last > 1) paginationBox.getChildren().add(ellipsis());
            paginationBox.getChildren().add(numberNode(n));
            last = n;
        }

        paginationBox.getChildren().add(navButton("Next", page < pages, () -> goTo(page + 1)));
    }

    private Button navButton(String text, boolean enabled, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("adm-page-btn");
        b.setDisable(!enabled);
        b.setFocusTraversable(false);
        b.setOnAction(e -> action.run());
        return b;
    }

    private Node numberNode(int n) {
        if (n == page) {
            Label current = new Label(String.valueOf(n));
            current.getStyleClass().add("adm-page-num-active");
            return current;
        }
        Button b = new Button(String.valueOf(n));
        b.getStyleClass().add("adm-page-num");
        b.setFocusTraversable(false);
        b.setOnAction(e -> goTo(n));
        return b;
    }

    private static Label ellipsis() {
        Label l = new Label("…");
        l.getStyleClass().add("adm-ellipsis");
        return l;
    }

    private void goTo(int target) {
        page = target;
        render();
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    @FXML
    private void handleSort() {
        newestFirst = !newestFirst;
        sortBtn.setText(newestFirst ? "⇅   Newest first" : "⇅   Oldest first");
        resetAndRender();
    }

    @FXML
    private void handleBack() {
        shell.navigate(CashierPage.HOME);
    }
}