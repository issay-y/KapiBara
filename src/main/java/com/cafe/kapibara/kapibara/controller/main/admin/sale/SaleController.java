package com.cafe.kapibara.kapibara.controller.main.admin.sale;

import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.SaleLine;
import com.cafe.kapibara.kapibara.util.ChartUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/** Sales overview page (KPIs, performance chart, best sellers, hourly + monthly). */
public class SaleController implements Initializable, AdminShell.Aware {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    @FXML private Button dateRangeBtn;

    // KPI cards
    @FXML private Label todaySalesValue, todaySalesSub;
    @FXML private Label monthSalesValue, monthSalesSub;
    @FXML private Label txValue, txSub;
    @FXML private Label avgValue, avgSub;
    @FXML private Label topProductValue, topProductSub;

    // sales performance
    @FXML private Label perfSubtitle, perfScaleLabel;
    @FXML private ToggleGroup periodGroup;
    @FXML private ToggleButton dailyBtn, weeklyBtn, monthlyBtn;
    @FXML private BarChart<String, Number> perfChart;

    // best sellers
    @FXML private VBox bestSellersBox;
    @FXML private Label bestSellersFooter;

    // bottom charts
    @FXML private BarChart<String, Number> hourlyChart;
    @FXML private Label hourlyFooter;
    @FXML private Label monthlyTitle;
    @FXML private BarChart<String, Number> monthlyChart;
    @FXML private Label monthlyFooter;

    private AdminShell shell;
    private final CashierStore store = CashierStore.get();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        periodGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });

        LocalDate today = LocalDate.now();
        dateRangeBtn.setText(today.minusDays(6).format(DAY_MONTH) + " – "
                + today.format(DAY_MONTH_YEAR) + "     📅");
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;

        try {
            store.refreshSalesHistory();
        } catch (SQLException e) {
            System.err.println("Could not load sales history: " + e.getMessage());
        }

        loadDashboard();
    }

    // =====================================================================
    //  DATA PROCESSING
    // =====================================================================

    private void loadDashboard() {
        LocalDate today = LocalDate.now();
        List<Sale> allSales = store.sales();

        double todaySales = 0;
        int todayTx = 0;
        double monthSales = 0;
        int monthTx = 0;

        for (Sale sale : allSales) {
            LocalDate date = sale.time().toLocalDate();
            double total = sale.total().doubleValue();

            if (date.getYear() == today.getYear() && date.getMonth() == today.getMonth()) {
                monthSales += total;
                monthTx++;

                if (date.equals(today)) {
                    todaySales += total;
                    todayTx++;
                }
            }
        }

        todaySalesValue.setText(peso(todaySales));
        todaySalesSub.setText(today.format(DateTimeFormatter.ofPattern("dd MMMM", Locale.ENGLISH))
                + " · " + todayTx + " transactions");

        monthSalesValue.setText(peso(monthSales));
        monthSalesSub.setText(today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " to date · " + monthTx + " transactions");

        txValue.setText(String.valueOf(todayTx));
        txSub.setText("Today · All complete");

        avgValue.setText(todayTx > 0 ? peso(todaySales / todayTx) : "₱0");
        avgSub.setText("Today's sales ÷ " + todayTx + " orders");

        loadBestSellers();
        showPeriod(weeklyBtn);
        loadHourlyChart();
        loadMonthlyChart();
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
        sortedStats.sort((a, b) -> Integer.compare(b.sold, a.sold));

        bestSellersBox.getChildren().clear();

        if (sortedStats.isEmpty()) {
            topProductValue.setText("No sales yet");
            topProductSub.setText("Waiting for first order");

            Label empty = new Label("No items sold today.");
            empty.getStyleClass().add("adm-seller-label");
            bestSellersBox.getChildren().add(empty);
            bestSellersFooter.setText("0 items sold today");
            return;
        }

        ProductStats top = sortedStats.get(0);
        topProductValue.setText(top.name);
        topProductSub.setText(top.sold + " sold today · " + peso(top.revenue));

        int max = top.sold;
        int displayCount = Math.min(sortedStats.size(), 5);

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

        int otherItemsCount = Math.max(0, sortedStats.size() - displayCount);
        bestSellersFooter.setText(sortedStats.size() + " unique products sold today • Other products: " + otherItemsCount);
    }

    private static class ProductStats {
        String name;
        int sold = 0;
        double revenue = 0;
        ProductStats(String name) { this.name = name; }
    }

    private void loadHourlyChart() {
        LocalDate today = LocalDate.now();
        int startHour = 7;
        int endHour = 18;
        List<String> labels = new ArrayList<>();
        double[] values = new double[endHour - startHour + 1];

        for (int i = startHour; i <= endHour; i++) {
            labels.add(i <= 12 ? (i == 12 ? "12 PM" : i + " AM") : (i - 12) + " PM");
        }

        double sum = 0;
        for (Sale sale : store.sales()) {
            if (sale.time().toLocalDate().equals(today)) {
                int h = sale.time().getHour();
                if (h >= startHour && h <= endHour) {
                    values[h - startHour] += sale.total().doubleValue();
                    sum += sale.total().doubleValue();
                }
            }
        }

        ChartUtil.fillBars(hourlyChart, labels, values, values.length - 1);
        hourlyFooter.setText("Today so far • " + peso(sum) + " total");
    }

    private void loadMonthlyChart() {
        LocalDate today = LocalDate.now();
        int days = today.getDayOfMonth();
        List<String> labels = new ArrayList<>();
        double[] values = new double[days];

        for (int i = 1; i <= days; i++) {
            labels.add(String.format("%02d", i));
        }

        double sum = 0;
        for (Sale s : store.sales()) {
            LocalDate d = s.time().toLocalDate();
            if (d.getYear() == today.getYear() && d.getMonth() == today.getMonth()) {
                int dayIndex = d.getDayOfMonth() - 1;
                values[dayIndex] += s.total().doubleValue();
                sum += s.total().doubleValue();
            }
        }

        ChartUtil.fillBars(monthlyChart, labels, values, values.length - 1);

        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        monthlyTitle.setText("Monthly • " + month + " to date");
        monthlyFooter.setText("01–" + String.format("%02d", days) + " " + month + " • " + peso(sum) + " total");
    }

    // =====================================================================
    //  SALES PERFORMANCE (Daily / Weekly / Monthly)
    // =====================================================================

    @FXML
    private void handlePeriodChange(ActionEvent event) {
        showPeriod((ToggleButton) event.getSource());
    }

    private void showPeriod(ToggleButton selected) {
        selected.setSelected(true);
        List<String> labels = new ArrayList<>();
        double[] values;
        double sum = 0;

        LocalDate today = LocalDate.now();
        List<Sale> sales = store.sales();

        if (selected == dailyBtn) {
            int startHour = 7;
            int endHour = 18;
            values = new double[endHour - startHour + 1];

            for (int i = startHour; i <= endHour; i++) {
                labels.add(i <= 12 ? (i == 12 ? "12 PM" : i + " AM") : (i - 12) + " PM");
            }

            for (Sale s : sales) {
                if (s.time().toLocalDate().equals(today)) {
                    int h = s.time().getHour();
                    if (h >= startHour && h <= endHour) {
                        values[h - startHour] += s.total().doubleValue();
                    }
                }
            }
            perfScaleLabel.setText("PHP · Hourly sales for today");

        } else if (selected == monthlyBtn) {
            values = new double[6];
            for (int i = 5; i >= 0; i--) {
                labels.add(today.minusMonths(i).format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)));
            }

            for (Sale s : sales) {
                LocalDate d = s.time().toLocalDate();
                long monthsBetween = ChronoUnit.MONTHS.between(d.withDayOfMonth(1), today.withDayOfMonth(1));
                if (monthsBetween >= 0 && monthsBetween < 6) {
                    values[5 - (int) monthsBetween] += s.total().doubleValue();
                }
            }
            perfScaleLabel.setText("PHP · Sales over the last 6 months");

        } else {
            values = new double[7];
            for (int i = 6; i >= 0; i--) {
                labels.add(today.minusDays(i).format(DAY_MONTH));
            }

            for (Sale s : sales) {
                LocalDate d = s.time().toLocalDate();
                long daysBetween = ChronoUnit.DAYS.between(d, today);
                if (daysBetween >= 0 && daysBetween < 7) {
                    values[6 - (int) daysBetween] += s.total().doubleValue();
                }
            }
            perfScaleLabel.setText("PHP · Sales over the last 7 days");
        }

        for (double v : values) sum += v;

        ChartUtil.fillBars(perfChart, labels, values, values.length - 1);
        perfSubtitle.setText(peso(sum) + " total");
    }

    @FXML
    private void handleDateRange(ActionEvent event) {
        // TODO: open a date range picker, then reload the page for that range
    }

    private static String peso(double amount) {
        return String.format("₱%,.0f", amount);
    }
}