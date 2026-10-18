package com.cafe.kapibara.kapibara.controller.main.admin.transaction;

import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.SaleLine;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;


public class TransactionController implements Initializable, AdminShell.Aware {

    private static final int PAGE_SIZE = 6;
    private static final int COLUMNS = 8;

    private static final String DATE_TODAY = "Today";
    private static final String DATE_YESTERDAY = "Yesterday";
    private static final String DATE_7_DAYS = "Last 7 days";
    private static final String DATE_MONTH = "This month";
    private static final String DATE_ALL = "All time";

    private static final String PAY_ALL = "All payment statuses";
    private static final String PAY_PAID = "Paid";

    private static final String STATUS_ALL = "All transactions";
    private static final String STATUS_COMPLETE = "Complete";

    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM, h:mm a", Locale.ENGLISH);

    @FXML private TextField searchField;
    @FXML private ComboBox<String> dateCombo, paymentCombo, statusCombo;
    @FXML private Button sortBtn;
    @FXML private GridPane txGrid;
    @FXML private Label showingLabel, todayLabel;
    @FXML private HBox paginationBox;

    private AdminShell shell;
    private final CashierStore store = CashierStore.get();

    private boolean newestFirst = true;
    private int page = 1;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        dateCombo.getItems().setAll(DATE_TODAY, DATE_YESTERDAY, DATE_7_DAYS, DATE_MONTH, DATE_ALL);
        dateCombo.getSelectionModel().selectFirst();

        paymentCombo.getItems().setAll(PAY_ALL, PAY_PAID);
        paymentCombo.getSelectionModel().selectFirst();

        statusCombo.getItems().setAll(STATUS_ALL, STATUS_COMPLETE);
        statusCombo.getSelectionModel().selectFirst();

        searchField.textProperty().addListener((o, a, b) -> resetAndRefresh());
        dateCombo.valueProperty().addListener((o, a, b) -> resetAndRefresh());
        paymentCombo.valueProperty().addListener((o, a, b) -> resetAndRefresh());
        statusCombo.valueProperty().addListener((o, a, b) -> resetAndRefresh());
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;
        try {
            store.refreshSalesHistory();
        } catch (SQLException e) {
            System.err.println("Could not load sales history: " + e.getMessage());
        }
        refresh();
    }

    private static String txNumber(Sale sale) {
        return sale.number();
    }

    private static String cashierName(Sale sale) {
        return sale.cashier();
    }

    private static int itemCount(Sale sale) {
        int n = 0;
        for (SaleLine line : sale.lines()) n += line.qty();
        return n;
    }

    private boolean dateMatches(LocalDate d) {
        LocalDate today = LocalDate.now();
        String f = dateCombo.getValue();
        if (f == null || f.equals(DATE_ALL)) return true;
        return switch (f) {
            case DATE_TODAY -> d.equals(today);
            case DATE_YESTERDAY -> d.equals(today.minusDays(1));
            case DATE_7_DAYS -> !d.isAfter(today) && !d.isBefore(today.minusDays(6));
            case DATE_MONTH -> d.getYear() == today.getYear() && d.getMonth() == today.getMonth();
            default -> true;
        };
    }

    private List<Sale> filtered() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ENGLISH);
        List<Sale> out = new ArrayList<>();

        for (Sale s : store.sales()) {
            if (!dateMatches(s.time().toLocalDate())) continue;
            if (!q.isEmpty()) {
                String hay = (txNumber(s) + " " + cashierName(s)).toLowerCase(Locale.ENGLISH);
                if (!hay.contains(q)) continue;
            }
            out.add(s);
        }

        Comparator<Sale> byTime = Comparator.comparing(Sale::time);
        out.sort(newestFirst ? byTime.reversed() : byTime);
        return out;
    }

    private void resetAndRefresh() {
        page = 1;
        refresh();
    }

    private void refresh() {
        List<Sale> rows = filtered();
        int total = rows.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        if (page > pages) page = pages;

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(total, from + PAGE_SIZE);

        buildGrid(rows.subList(from, to), dateCombo.getValue());
        buildPagination(pages);

        showingLabel.setText(total == 0
                ? "No transactions to show"
                : "Showing " + (from + 1) + "–" + to + " of " + total + " transactions");

        double todayTotal = 0;
        LocalDate today = LocalDate.now();
        for (Sale s : store.sales()) {
            if (s.time().toLocalDate().equals(today)) todayTotal += s.total().doubleValue();
        }
        todayLabel.setText("Today's completed sales: " + peso(todayTotal));
    }

    private void buildGrid(List<Sale> rows, String dateFilter) {
        txGrid.getChildren().clear();
        txGrid.getRowConstraints().clear();

        String[] heads = {"Transaction no.", "Time", "Cashier", "Items", "Total", "Payment", "Status", "Action"};
        for (int c = 0; c < heads.length; c++) {
            Label h = new Label(heads[c]);
            h.getStyleClass().add("adm-th");
            h.setMaxWidth(Double.MAX_VALUE);
            txGrid.add(h, c, 0);
        }

        if (rows.isEmpty()) {
            Label empty = new Label("No transactions found.");
            empty.getStyleClass().add("adm-td");
            empty.setMaxWidth(Double.MAX_VALUE);
            txGrid.add(empty, 0, 1, COLUMNS, 1);
            return;
        }

        boolean timeOnly = DATE_TODAY.equals(dateFilter);
        int r = 1;
        for (Sale s : rows) {
            txGrid.add(cell(txNumber(s)), 0, r);
            txGrid.add(cell(s.time().format(timeOnly ? TIME_ONLY : DATE_TIME)), 1, r);
            txGrid.add(cell(cashierName(s)), 2, r);
            txGrid.add(cell(String.valueOf(itemCount(s))), 3, r);
            txGrid.add(cell(String.format("₱%,.2f", s.total().doubleValue())), 4, r);
            txGrid.add(pill("Paid"), 5, r);
            txGrid.add(pill("Complete"), 6, r);

            Button view = new Button("View →");
            view.getStyleClass().add("adm-link");
            view.setOnAction(e -> showDetails(s));
            HBox viewBox = new HBox(view);
            viewBox.setAlignment(Pos.CENTER_LEFT);
            viewBox.getStyleClass().add("adm-td");
            txGrid.add(viewBox, 7, r);

            // one straight line under each row
            Region line = new Region();
            line.getStyleClass().add("adm-row-line");
            line.setMinHeight(1);
            line.setPrefHeight(1);
            line.setMaxHeight(1);
            line.setMouseTransparent(true);
            GridPane.setValignment(line, VPos.BOTTOM);
            GridPane.setHgrow(line, javafx.scene.layout.Priority.ALWAYS);
            txGrid.add(line, 0, r, COLUMNS, 1);
            r++;
        }
    }

    private Label cell(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("adm-td");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private HBox pill(String text) {
        Label p = new Label(text);
        p.getStyleClass().add("adm-pill-green");
        HBox box = new HBox(p);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("adm-td");
        return box;
    }

    private void buildPagination(int pages) {
        paginationBox.getChildren().clear();
        paginationBox.setAlignment(Pos.CENTER_RIGHT);

        Button prev = new Button("Previous");
        prev.getStyleClass().add("adm-page-btn");
        prev.setDisable(page <= 1);
        prev.setOnAction(e -> { page--; refresh(); });
        paginationBox.getChildren().add(prev);

        int start = Math.max(1, page - 2);
        int end = Math.min(pages, page + 2);
        for (int p = start; p <= end; p++) {
            if (p == page) {
                Label active = new Label(String.valueOf(p));
                active.getStyleClass().add("adm-page-num-active");
                paginationBox.getChildren().add(active);
            } else {
                final int target = p;
                Button b = new Button(String.valueOf(p));
                b.getStyleClass().add("adm-page-num");
                b.setOnAction(e -> { page = target; refresh(); });
                paginationBox.getChildren().add(b);
            }
        }

        Button next = new Button("Next");
        next.getStyleClass().add("adm-page-btn");
        next.setDisable(page >= pages);
        next.setOnAction(e -> { page++; refresh(); });
        paginationBox.getChildren().add(next);
    }

    @FXML private void handleSort(ActionEvent event) {
        newestFirst = !newestFirst;
        sortBtn.setText(newestFirst ? "⇅   Newest first" : "⇅   Oldest first");
        resetAndRefresh();
    }

    @FXML private void handleBack(ActionEvent event) {
        shell.navigate(AdminPage.HOME);
    }

    private void showDetails(Sale sale) {
        store.setViewedSale(sale);
        shell.navigate(AdminPage.TRANSACTION_DETAIL);
    }

    private static String peso(double amount) {
        return String.format("₱%,.2f", amount);
    }
}