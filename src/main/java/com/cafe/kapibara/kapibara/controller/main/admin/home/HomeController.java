package com.cafe.kapibara.kapibara.controller.main.admin.home;

import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
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

import java.net.URL;
import java.time.LocalTime;
import java.util.List;
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadKpis();
        loadRhythmChart();
        loadBestSellers();
        loadRecentTransactions();
        loadAttention();
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;
        greetingLabel.setText(greeting() + ", " + firstName(shell.getCurrentUserName())
                + ". Your team, records and stock in one view.");
    }

    // =====================================================================
    //  DATA  (sample values: replace with DAO / SQL queries)
    // =====================================================================

    private void loadKpis() {
        // TODO: replace with real queries (SalesDAO, InventoryDAO, ...)
        double todaySales = 18480;
        int todayTx = 48;
        double monthSales = 221760;
        int monthOrders = 576;
        int inStock = 18, totalItems = 24, lowStock = 4, outOfStock = 2;

        todaySalesValue.setText(peso(todaySales));
        todaySalesSub.setText(todayTx + " completed cash transactions");

        monthSalesValue.setText(peso(monthSales));
        monthSalesSub.setText("October to date · " + monthOrders + " orders");

        txValue.setText(String.valueOf(todayTx));
        txSub.setText(peso(todaySales / todayTx) + " average transaction");

        inventoryValue.setText(inStock + " / " + totalItems);
        inventorySub.setText(lowStock + " low stock · " + outOfStock + " out of stock");
    }

    private void loadRhythmChart() {
        List<String> labels = List.of("7 AM", "8 AM", "9 AM", "10 AM");
        double[] values = {3080, 5390, 6545, 3465};
        ChartUtil.fillBars(rhythmChart, labels, values, values.length - 1);
        rhythmTotal.setText(String.format("₱%,.2f", ChartUtil.sum(values)));
    }

    private void loadBestSellers() {
        String[] names = {"Spanish Latte", "Mocha", "Iced Coffee", "Café Latte", "Croissant"};
        int[] sold = {38, 27, 24, 18, 15};
        int[] sales = {4750, 3645, 2280, 2160, 1275};

        bestSellersBox.getChildren().clear();
        int max = sold[0];
        for (int i = 0; i < names.length; i++) {
            Label label = new Label(names[i] + " · " + sold[i] + " sold · " + peso(sales[i]));
            label.getStyleClass().add("adm-seller-label");

            ProgressBar bar = new ProgressBar((double) sold[i] / max);
            bar.getStyleClass().add("adm-seller-bar");
            bar.setMaxWidth(Double.MAX_VALUE);
            if (i == 0) bar.getStyleClass().add("adm-top-seller");

            bestSellersBox.getChildren().add(new VBox(4, label, bar));
        }
    }

    private void loadRecentTransactions() {
        String[][] rows = {
                {"KB-20261006-048", "Mika Santos", "₱385.00", "Complete"},
                {"KB-20261006-047", "Mika Santos", "₱420.00", "Complete"},
                {"KB-20261006-046", "Mika Santos", "₱240.00", "Complete"},
        };
        String[] headers = {"Transaction", "Cashier", "Total", "Status"};

        recentGrid.getChildren().clear();
        for (int c = 0; c < headers.length; c++) {
            Label h = new Label(headers[c]);
            h.getStyleClass().add("adm-th");
            h.setMaxWidth(Double.MAX_VALUE);
            recentGrid.add(h, c, 0);
        }

        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < 3; c++) {
                Label cell = new Label(rows[r][c]);
                cell.getStyleClass().add("adm-td");
                cell.setMaxWidth(Double.MAX_VALUE);
                recentGrid.add(cell, c, r + 1);
            }
            Label pill = new Label(rows[r][3]);
            pill.getStyleClass().add("adm-pill-green");
            HBox pillCell = new HBox(pill);
            pillCell.setAlignment(Pos.CENTER_LEFT);
            pillCell.getStyleClass().add("adm-td");
            recentGrid.add(pillCell, 3, r + 1);
        }
    }

    private void loadAttention() {
        attentionTitle.setText("6 items need attention");
        attentionSub.setText("2 unavailable • 1 critical • 3 low stock");

        String[][] stock = {
                {"Caramel syrup", "0 liters"},
                {"Milk foam mix", "0 packs"},
                {"Takeaway cups", "18 / 50 pcs"},
        };
        stockRowsBox.getChildren().clear();
        for (String[] s : stock) {
            Label name = new Label(s[0]);
            name.getStyleClass().add("adm-stock-name");
            Label value = new Label(s[1]);
            value.getStyleClass().add("adm-stock-value");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            stockRowsBox.getChildren().add(new HBox(name, spacer, value));
        }

        teamFooter.setText("5 active accounts • 1 inactive account");
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