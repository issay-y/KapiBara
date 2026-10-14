package com.cafe.kapibara.kapibara.controller.main.admin.sale;

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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/** Sales overview page (the old dashboard: KPIs, performance chart, best sellers, hourly + monthly). */
public class SaleController implements Initializable {

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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Always keep one period selected
        periodGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });

        LocalDate today = LocalDate.now();
        dateRangeBtn.setText(today.minusDays(6).format(DAY_MONTH) + " – "
                + today.format(DAY_MONTH_YEAR) + "     📅");

        loadDashboard();
    }

    // =====================================================================
    //  DATA  (sample values: replace with DAO / SQL queries)
    // =====================================================================

    private void loadDashboard() {
        // TODO: replace each of these with real queries (e.g. SalesDAO.getTodayTotal())
        double todaySales = 18480;
        int todayTx = 48;
        double monthSales = 221760;
        int monthTx = 576;

        todaySalesValue.setText(peso(todaySales));
        todaySalesSub.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM", Locale.ENGLISH))
                + " · " + todayTx + " transactions");

        monthSalesValue.setText(peso(monthSales));
        monthSalesSub.setText(LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " to date · " + monthTx + " transactions");

        txValue.setText(String.valueOf(todayTx));
        txSub.setText("Today · All complete");

        avgValue.setText(peso(todaySales / todayTx));
        avgSub.setText("Today's sales ÷ " + todayTx + " orders");

        loadBestSellers();
        showPeriod(weeklyBtn);
        loadHourlyChart();
        loadMonthlyChart();
    }

    private void loadBestSellers() {
        String[] names = {"Spanish Latte", "Mocha", "Iced Coffee", "Café Latte", "Croissant"};
        int[] sold = {38, 27, 24, 18, 15};
        int[] sales = {4750, 3645, 2280, 2160, 1275};

        topProductValue.setText(names[0]);
        topProductSub.setText(sold[0] + " cups today · " + peso(sales[0]));

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
        bestSellersFooter.setText("122 of 168 items • Other products: 46 items");
    }

    private void loadHourlyChart() {
        List<String> labels = List.of("7 AM", "8 AM", "9 AM", "10 AM");
        double[] values = {3080, 5390, 6545, 3465};
        ChartUtil.fillBars(hourlyChart, labels, values, values.length - 1);
        hourlyFooter.setText("Today so far • " + peso(ChartUtil.sum(values)) + " total");
    }

    private void loadMonthlyChart() {
        List<String> labels = List.of("01", "02", "03", "04", "05", "06");
        double[] values = {36960, 40425, 42350, 46200, 37345, 18480};
        ChartUtil.fillBars(monthlyChart, labels, values, values.length - 1);

        String month = LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        monthlyTitle.setText("Monthly • " + month + " to date");
        monthlyFooter.setText("01–06 " + month + " • " + peso(ChartUtil.sum(values)) + " total");
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

        if (selected == dailyBtn) {                       // by hour, today
            labels = List.of("7 AM", "8 AM", "9 AM", "10 AM");
            values = new double[]{3080, 5390, 6545, 3465};
            perfScaleLabel.setText("PHP · Scale 0 – 7,000");
        } else if (selected == monthlyBtn) {              // last 6 months
            LocalDate today = LocalDate.now();
            for (int i = 5; i >= 0; i--) {
                labels.add(today.minusMonths(i).format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)));
            }
            values = new double[]{198400, 212300, 205900, 231500, 244800, 221760};
            perfScaleLabel.setText("PHP · Scale 0 – 250,000");
        } else {                                          // weekly: last 7 days
            LocalDate today = LocalDate.now();
            for (int i = 6; i >= 0; i--) {
                labels.add(today.minusDays(i).format(DAY_MONTH));
            }
            values = new double[]{30800, 36960, 40425, 42350, 46200, 37345, 18480};
            perfScaleLabel.setText("PHP · Scale 0 – 50,000");
        }

        ChartUtil.fillBars(perfChart, labels, values, values.length - 1);
        perfSubtitle.setText(peso(ChartUtil.sum(values)) + " total");
    }

    @FXML
    private void handleDateRange(ActionEvent event) {
        // TODO: open a date range picker, then reload the page for that range
    }

    private static String peso(double amount) {
        return String.format("₱%,.0f", amount);
    }
}