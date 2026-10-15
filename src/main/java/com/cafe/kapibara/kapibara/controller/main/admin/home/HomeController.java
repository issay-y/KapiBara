package com.cafe.kapibara.kapibara.controller.main.admin.home;

import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.SaleLine;
import com.cafe.kapibara.kapibara.util.ChartUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class HomeController implements Initializable, AdminShell.Aware {

    @FXML private Label greetingLabel;

    // KPI cards
    @FXML private Label todaySalesValue, todaySalesSub;
    @FXML private Label monthSalesValue, monthSalesSub;
    @FXML private Label txValue, txSub;
    @FXML private Label inventoryValue, inventorySub;

    // today's sales rhythm
    @FXML private BarChart<String, Number> rhythmChart;
    @FXML private Label rhythmTotal;

    // best sellers
    @FXML private VBox bestSellersBox;

    // recent transactions
    @FXML private GridPane recentGrid;

    // team & stock attention
    @FXML private Label attentionTitle, attentionSub;
    @FXML private VBox stockRowsBox;
    @FXML private Label teamFooter;

    private AdminShell shell;
    private final CashierStore store = CashierStore.get();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialization handled in attach() after data is fetched
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;
        greetingLabel.setText(greeting() + ", " + firstName(shell.getCurrentUserName())
                + ". Your team, records and stock in one view.");

        try {
            store.refreshSalesHistory();
            store.refreshMenu(); // Fetch latest inventory for stock alerts
        } catch (SQLException e) {
            System.err.println("Database load error: " + e.getMessage());
        }

        render();
    }

    private void render() {
        loadKpis();
        loadRhythmChart();
        loadBestSellers();
        loadRecentTransactions();
        loadAttention();
    }

    // =====================================================================
    //  DATA PROCESSING
    // =====================================================================

    private void loadKpis() {
        LocalDate today = LocalDate.now();
        List<Sale> allSales = store.sales();

        double todaySales = 0;
        int todayTx = 0;
        double monthSales = 0;
        int monthOrders = 0;

        for (Sale sale : allSales) {
            LocalDate saleDate = sale.time().toLocalDate();
            double total = sale.total().doubleValue();

            if (saleDate.getYear() == today.getYear() && saleDate.getMonth() == today.getMonth()) {
                monthSales += total;
                monthOrders++;

                if (saleDate.equals(today)) {
                    todaySales += total;
                    todayTx++;
                }
            }
        }

        List<Product> products = store.products();
        int totalItems = products.size();
        int inStock = 0, lowStock = 0, outOfStock = 0;

        for (Product p : products) {
            if (!p.isAvailable()) outOfStock++;
            else if (p.isLow()) lowStock++;
            else inStock++;
        }

        todaySalesValue.setText(peso(todaySales));
        todaySalesSub.setText(todayTx + " completed cash transactions");

        monthSalesValue.setText(peso(monthSales));
        monthSalesSub.setText("October to date · " + monthOrders + " orders");

        txValue.setText(String.valueOf(todayTx));
        txSub.setText(todayTx > 0 ? peso(todaySales / todayTx) + " average transaction" : "₱0 average");

        inventoryValue.setText((inStock + lowStock) + " / " + totalItems);
        inventorySub.setText(lowStock + " low stock · " + outOfStock + " out of stock");
    }

    private void loadRhythmChart() {
        LocalDate today = LocalDate.now();
        double[] values = new double[4]; // 7 AM, 8 AM, 9 AM, 10 AM
        List<String> labels = List.of("7 AM", "8 AM", "9 AM", "10 AM");

        double totalSum = 0;

        for (Sale sale : store.sales()) {
            if (sale.time().toLocalDate().equals(today)) {
                int hour = sale.time().getHour();
                if (hour == 7) values[0] += sale.total().doubleValue();
                else if (hour == 8) values[1] += sale.total().doubleValue();
                else if (hour == 9) values[2] += sale.total().doubleValue();
                else if (hour == 10) values[3] += sale.total().doubleValue();

                totalSum += sale.total().doubleValue();
            }
        }

        ChartUtil.fillBars(rhythmChart, labels, values, values.length - 1);
        rhythmTotal.setText(String.format("₱%,.2f", totalSum));
    }

    private void loadBestSellers() {
        LocalDate today = LocalDate.now();
        Map<String, ProductStats> statsMap = new HashMap<>();

        for (Sale sale : store.sales()) {
            if (sale.time().toLocalDate().equals(today)) {
                for (SaleLine line : sale.lines()) {
                    statsMap.putIfAbsent(line.name(), new ProductStats(line.name()));
                    ProductStats stat = statsMap.get(line.name());
                    stat.sold += line.qty();
                    stat.revenue += line.amount().doubleValue();
                }
            }
        }

        List<ProductStats> sortedStats = new ArrayList<>(statsMap.values());
        sortedStats.sort((a, b) -> Integer.compare(b.sold, a.sold)); // Descending by sold qty

        bestSellersBox.getChildren().clear();
        if (sortedStats.isEmpty()) {
            Label empty = new Label("No sales today yet.");
            empty.getStyleClass().add("adm-seller-label");
            bestSellersBox.getChildren().add(empty);
            return;
        }

        int max = sortedStats.get(0).sold;
        int displayCount = Math.min(sortedStats.size(), 5); // Show top 5

        for (int i = 0; i < displayCount; i++) {
            ProductStats stat = sortedStats.get(i);
            Label label = new Label(stat.name + " · " + stat.sold + " sold · " + peso(stat.revenue));
            label.getStyleClass().add("adm-seller-label");

            ProgressBar bar = new ProgressBar((double) stat.sold / max);
            bar.getStyleClass().add("adm-seller-bar");
            bar.setMaxWidth(Double.MAX_VALUE);
            if (i == 0) bar.getStyleClass().add("adm-top-seller");

            bestSellersBox.getChildren().add(new VBox(4, label, bar));
        }
    }

    private static class ProductStats {
        String name;
        int sold = 0;
        double revenue = 0;
        ProductStats(String name) { this.name = name; }
    }

    private void loadRecentTransactions() {
        List<Sale> recent = store.recentSales(3);
        String[] headers = {"Transaction", "Cashier", "Total", "Status"};

        recentGrid.getChildren().clear();
        for (int c = 0; c < headers.length; c++) {
            Label h = new Label(headers[c]);
            h.getStyleClass().add("adm-th");
            h.setMaxWidth(Double.MAX_VALUE);
            recentGrid.add(h, c, 0);
        }

        if (recent.isEmpty()) {
            Label empty = new Label("No recent transactions.");
            empty.getStyleClass().add("adm-td");
            recentGrid.add(empty, 0, 1, 4, 1);
            return;
        }

        for (int r = 0; r < recent.size(); r++) {
            Sale sale = recent.get(r);

            Label numLabel = new Label(sale.number());
            numLabel.getStyleClass().add("adm-td");
            numLabel.setMaxWidth(Double.MAX_VALUE);
            recentGrid.add(numLabel, 0, r + 1);

            Label cashierLabel = new Label(sale.cashier());
            cashierLabel.getStyleClass().add("adm-td");
            cashierLabel.setMaxWidth(Double.MAX_VALUE);
            recentGrid.add(cashierLabel, 1, r + 1);

            Label totalLabel = new Label(CashierStore.money(sale.total()));
            totalLabel.getStyleClass().add("adm-td");
            totalLabel.setMaxWidth(Double.MAX_VALUE);
            recentGrid.add(totalLabel, 2, r + 1);

            Label pill = new Label("Complete");
            pill.getStyleClass().add("adm-pill-green");
            HBox pillCell = new HBox(pill);
            pillCell.setAlignment(Pos.CENTER_LEFT);
            pillCell.getStyleClass().add("adm-td");
            recentGrid.add(pillCell, 3, r + 1);
        }
    }

    private void loadAttention() {
        List<Product> products = store.products();
        List<Product> issueProducts = new ArrayList<>();
        int unavailable = 0;
        int low = 0;

        for (Product p : products) {
            if (!p.isAvailable()) {
                unavailable++;
                issueProducts.add(p);
            } else if (p.isLow()) {
                low++;
                issueProducts.add(p);
            }
        }

        int totalIssues = unavailable + low;
        attentionTitle.setText(totalIssues + (totalIssues == 1 ? " item needs" : " items need") + " attention");
        attentionSub.setText(unavailable + " unavailable • " + low + " low stock");

        stockRowsBox.getChildren().clear();
        int displayCount = Math.min(issueProducts.size(), 3);

        for (int i = 0; i < displayCount; i++) {
            Product p = issueProducts.get(i);
            Label name = new Label(p.getName());
            name.getStyleClass().add("adm-stock-name");

            Label value = new Label(p.getStock() + " " + p.getUnit());
            value.getStyleClass().add("adm-stock-value");
            if (!p.isAvailable()) value.setStyle("-fx-text-fill: -adm-red;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            stockRowsBox.getChildren().add(new HBox(name, spacer, value));
        }

        teamFooter.setText("See Accounts tab for user management");
    }

    // =====================================================================
    //  NAVIGATION LINKS
    // =====================================================================

    @FXML private void handleOpenSales(ActionEvent e)        { if (shell != null) shell.navigate(AdminPage.SALES); }
    @FXML private void handleOpenTransactions(ActionEvent e) { if (shell != null) shell.navigate(AdminPage.TRANSACTIONS); }
    @FXML private void handleOpenAccounts(ActionEvent e)     { if (shell != null) shell.navigate(AdminPage.ACCOUNTS); }

    // =====================================================================
    //  HELPERS
    // =====================================================================

    private static String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 18) return "Good afternoon";
        return "Good evening";
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "there";
        String first = fullName.trim().split("\\s+")[0];
        return first.substring(0, 1).toUpperCase() + first.substring(1);
    }

    private static String peso(double amount) {
        return String.format("₱%,.0f", amount);
    }
}