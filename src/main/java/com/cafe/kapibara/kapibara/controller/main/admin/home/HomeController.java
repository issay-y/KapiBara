package com.cafe.kapibara.kapibara.controller.main.admin.home;

import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
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

    // Ids that already exist in HomeView.fxml. They are only used to locate the old cards
    // (one in the top row, one in the bottom row) so they can be replaced in code.
    @FXML private BarChart<String, Number> rhythmChart;
    @FXML private GridPane recentGrid;

    // The new lower section (built in code, mounted once)
    private VBox lowerSection;

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
        mountLowerSection();
    }

    /** Replaces the old four cards with the new section, found via the old FXML ids. */
    private void mountLowerSection() {
        VBox fresh = new HomeLowerSection(store,
                () -> { if (shell != null) shell.navigate(AdminPage.SALES); }).build();

        if (lowerSection != null && lowerSection.getParent() != null) {
            // Already mounted (page refreshed): swap in the new content
            lowerSection.getChildren().setAll(fresh.getChildren());
            return;
        }
        lowerSection = fresh;

        Node row1 = cardOf(rhythmChart).getParent();   // row holding rhythm + best sellers
        Node row2 = cardOf(recentGrid).getParent();    // row holding transactions + team
        if (!(row1.getParent() instanceof Pane host)) {
            System.err.println("HomeController: could not find the lower cards' container.");
            return;
        }

        int index = host.getChildren().indexOf(row1);
        lowerSection.getProperties().putAll(row1.getProperties()); // keeps grid/hgrow/vgrow settings
        host.getChildren().set(index, lowerSection);
        if (row2 != row1 && row2.getParent() == host) host.getChildren().remove(row2);
    }

    /** Walks up from a node to its enclosing card (style class "adm-card"). */
    private static Node cardOf(Node node) {
        Node n = node;
        while (n != null) {
            if (n.getStyleClass().contains("adm-card")) return n;
            n = n.getParent();
        }
        return node.getParent();
    }

    // =====================================================================
    //  KPI CARDS
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