package com.cafe.kapibara.kapibara.controller.main.admin.sale;

import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.SaleLine;
import com.cafe.kapibara.kapibara.util.ChartUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/** Sales overview page. The period selector (Daily..Yearly) drives every card on the page. */
public class SaleController implements Initializable, AdminShell.Aware {

    private enum Period { DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY }

    private static final Locale EN = Locale.ENGLISH;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd MMM", EN);
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd MMM yyyy", EN);
    private static final DateTimeFormatter MONTH_ONLY = DateTimeFormatter.ofPattern("MMM", EN);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", EN);
    private static final DateTimeFormatter WEEKDAY_DAY = DateTimeFormatter.ofPattern("EEE dd", EN);

    private static final int START_HOUR = 7;
    private static final int END_HOUR = 18;

    // period selector
    @FXML private ToggleGroup periodGroup;
    @FXML private ToggleButton dailyBtn, weeklyBtn, monthlyBtn, quarterlyBtn, yearlyBtn;
    @FXML private Label rangeLabel;

    // KPI cards
    @FXML private Label salesLabel, salesValue, salesSub;
    @FXML private Label prevLabel, prevValue, prevSub;
    @FXML private Label txLabel, txValue, txSub;
    @FXML private Label avgLabel, avgValue, avgSub;
    @FXML private Label topLabel, topProductValue, topProductSub;

    // sales performance
    @FXML private Label perfSubtitle, perfScaleLabel;
    @FXML private BarChart<String, Number> perfChart;

    // best sellers
    @FXML private Label bestSellersTitle;
    @FXML private VBox bestSellersBox;
    @FXML private Label bestSellersFooter;

    // bottom charts
    @FXML private Label hourlyTitle, hourlyNote, hourlyFooter;
    @FXML private BarChart<String, Number> hourlyChart;
    @FXML private Label trendTitle, trendNote, trendFooter;
    @FXML private BarChart<String, Number> trendChart;

    private AdminShell shell;
    private final CashierStore store = CashierStore.get();
    private Period period = Period.DAILY;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // never allow "nothing selected"
        periodGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });
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

    @FXML
    private void handlePeriodChange(ActionEvent event) {
        ToggleButton src = (ToggleButton) event.getSource();
        src.setSelected(true);

        if (src == dailyBtn) period = Period.DAILY;
        else if (src == weeklyBtn) period = Period.WEEKLY;
        else if (src == monthlyBtn) period = Period.MONTHLY;
        else if (src == quarterlyBtn) period = Period.QUARTERLY;
        else period = Period.YEARLY;

        loadDashboard();
    }

    // =====================================================================
    //  PERIOD HELPERS
    // =====================================================================

    /** First day of the current period. */
    private static LocalDate periodStart(Period p, LocalDate today) {
        return switch (p) {
            case DAILY -> today;
            case WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> today.withDayOfMonth(1);
            case QUARTERLY -> LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case YEARLY -> LocalDate.of(today.getYear(), 1, 1);
        };
    }

    /** First day AFTER the period that begins at start. */
    private static LocalDate periodEnd(Period p, LocalDate start) {
        return switch (p) {
            case DAILY -> start.plusDays(1);
            case WEEKLY -> start.plusWeeks(1);
            case MONTHLY -> start.plusMonths(1);
            case QUARTERLY -> start.plusMonths(3);
            case YEARLY -> start.plusYears(1);
        };
    }

    /** Start of the period n periods before the one beginning at start. */
    private static LocalDate shiftBack(Period p, LocalDate start, int n) {
        return switch (p) {
            case DAILY -> start.minusDays(n);
            case WEEKLY -> start.minusWeeks(n);
            case MONTHLY -> start.minusMonths(n);
            case QUARTERLY -> start.minusMonths(3L * n);
            case YEARLY -> start.minusYears(n);
        };
    }

    private static String periodName(Period p) {
        return switch (p) {
            case DAILY -> "Today";
            case WEEKLY -> "This week";
            case MONTHLY -> "This month";
            case QUARTERLY -> "This quarter";
            case YEARLY -> "This year";
        };
    }

    private static String previousName(Period p) {
        return switch (p) {
            case DAILY -> "Yesterday";
            case WEEKLY -> "Last week";
            case MONTHLY -> "Last month";
            case QUARTERLY -> "Last quarter";
            case YEARLY -> "Last year";
        };
    }

    private static String rangeText(Period p, LocalDate start) {
        return switch (p) {
            case DAILY -> start.format(DAY_MONTH_YEAR);
            case WEEKLY -> start.format(DAY_MONTH) + " – " + start.plusDays(6).format(DAY_MONTH_YEAR);
            case MONTHLY -> start.format(MONTH_YEAR);
            case QUARTERLY -> quarterLabel(start) + " · " + start.format(MONTH_ONLY) + " – "
                    + start.plusMonths(2).format(MONTH_ONLY) + " " + start.getYear();
            case YEARLY -> String.valueOf(start.getYear());
        };
    }

    private static String quarterLabel(LocalDate d) {
        return "Q" + ((d.getMonthValue() - 1) / 3 + 1) + " " + d.getYear();
    }

    private static String hourLabel(int h) {
        return h < 12 ? h + " AM" : (h == 12 ? "12 PM" : (h - 12) + " PM");
    }

    private static boolean inRange(Sale s, LocalDate from, LocalDate toExclusive) {
        LocalDate d = s.time().toLocalDate();
        return !d.isBefore(from) && d.isBefore(toExclusive);
    }

    // =====================================================================
    //  DASHBOARD
    // =====================================================================

    private void loadDashboard() {
        LocalDate today = LocalDate.now();
        LocalDate start = periodStart(period, today);
        LocalDate end = periodEnd(period, start);
        LocalDate prevStart = shiftBack(period, start, 1);

        List<Sale> all = store.sales();
        List<Sale> current = new ArrayList<>();
        double curTotal = 0;
        double prevTotal = 0;
        int prevTx = 0;

        for (Sale s : all) {
            if (inRange(s, start, end)) {
                current.add(s);
                curTotal += s.total().doubleValue();
            } else if (inRange(s, prevStart, start)) {
                prevTotal += s.total().doubleValue();
                prevTx++;
            }
        }
        int curTx = current.size();

        rangeLabel.setText(rangeText(period, start));

        // KPI 1: sales this period
        salesLabel.setText(period == Period.DAILY ? "Today's Sales" : periodName(period) + "'s Sales");
        salesValue.setText(peso(curTotal));
        salesSub.setText(rangeText(period, start) + " · " + curTx + " transactions");

        // KPI 2: previous period + change
        prevLabel.setText(previousName(period) + "'s Sales");
        prevValue.setText(peso(prevTotal));
        String change;
        if (prevTotal > 0) {
            double pct = (curTotal - prevTotal) / prevTotal * 100.0;
            change = String.format("%s%.1f%% vs now", pct >= 0 ? "▼ " : "▲ ", Math.abs(pct));
            // pct >= 0 means current is higher than previous, so previous was lower
            change = String.format("Now is %s%.1f%% %s", "", Math.abs(pct), pct >= 0 ? "higher" : "lower");
        } else {
            change = "No sales in that period";
        }
        prevSub.setText(prevTx + " transactions · " + change);

        // KPI 3 + 4
        txLabel.setText("Total Transactions");
        txValue.setText(String.valueOf(curTx));
        txSub.setText(periodName(period) + " · All complete");

        avgLabel.setText("Average Transaction");
        avgValue.setText(curTx > 0 ? peso(curTotal / curTx) : "₱0");
        avgSub.setText("Sales ÷ " + curTx + " orders");

        topLabel.setText("Most Sold Product");

        loadBestSellers(current);
        loadPerformance(current, today, start);
        loadHourly(current);
        loadTrend(all, start);
    }

    // =====================================================================
    //  BEST SELLERS
    // =====================================================================

    private void loadBestSellers(List<Sale> sales) {
        Map<String, ProductStats> statsMap = new HashMap<>();

        for (Sale sale : sales) {
            for (SaleLine line : sale.lines()) {
                ProductStats stat = statsMap.computeIfAbsent(line.name(), ProductStats::new);
                stat.sold += line.qty();
                stat.revenue += line.amount().doubleValue();
            }
        }

        List<ProductStats> sorted = new ArrayList<>(statsMap.values());
        sorted.sort((a, b) -> Integer.compare(b.sold, a.sold));

        String when = periodName(period).toLowerCase(EN);
        bestSellersTitle.setText(period == Period.DAILY ? "Today's best sellers"
                : "Best sellers · " + periodName(period));
        bestSellersBox.getChildren().clear();

        if (sorted.isEmpty()) {
            topProductValue.setText("No sales yet");
            topProductSub.setText("Nothing sold " + when);

            Label empty = new Label("No items sold " + when + ".");
            empty.getStyleClass().add("adm-seller-label");
            bestSellersBox.getChildren().add(empty);
            bestSellersFooter.setText("0 items sold " + when);
            return;
        }

        ProductStats top = sorted.get(0);
        topProductValue.setText(top.name);
        topProductSub.setText(top.sold + " sold · " + peso(top.revenue));

        int max = top.sold;
        int displayCount = Math.min(sorted.size(), 5);

        for (int i = 0; i < displayCount; i++) {
            ProductStats stat = sorted.get(i);
            Label label = new Label(stat.name + " · " + stat.sold + " sold · " + peso(stat.revenue));
            label.getStyleClass().add("adm-seller-label");

            ProgressBar bar = new ProgressBar((double) stat.sold / max);
            bar.getStyleClass().add("adm-seller-bar");
            bar.setMaxWidth(Double.MAX_VALUE);
            if (i == 0) bar.getStyleClass().add("adm-top-seller");

            bestSellersBox.getChildren().add(new VBox(4, label, bar));
        }

        int others = Math.max(0, sorted.size() - displayCount);
        bestSellersFooter.setText(sorted.size() + " unique products sold " + when
                + " • Other products: " + others);
    }

    private static class ProductStats {
        final String name;
        int sold = 0;
        double revenue = 0;
        ProductStats(String name) { this.name = name; }
    }

    // =====================================================================
    //  SALES PERFORMANCE (breakdown inside the selected period)
    // =====================================================================

    private void loadPerformance(List<Sale> sales, LocalDate today, LocalDate start) {
        List<String> labels = new ArrayList<>();
        int highlight;
        String note;

        switch (period) {
            case DAILY -> {
                for (int h = START_HOUR; h <= END_HOUR; h++) labels.add(hourLabel(h));
                highlight = Math.max(0, Math.min(END_HOUR - START_HOUR, java.time.LocalTime.now().getHour() - START_HOUR));
                note = "PHP · Hourly sales for today";
            }
            case WEEKLY -> {
                for (int i = 0; i < 7; i++) labels.add(start.plusDays(i).format(WEEKDAY_DAY));
                highlight = today.getDayOfWeek().getValue() - 1;
                note = "PHP · Daily sales this week";
            }
            case MONTHLY -> {
                int days = today.getDayOfMonth();
                for (int i = 1; i <= days; i++) labels.add(String.format("%02d", i));
                highlight = days - 1;
                note = "PHP · Daily sales this month";
            }
            case QUARTERLY -> {
                for (int i = 0; i < 3; i++) labels.add(start.plusMonths(i).format(MONTH_ONLY));
                highlight = today.getMonthValue() - start.getMonthValue();
                note = "PHP · Monthly sales this quarter";
            }
            default -> {
                for (int i = 0; i < 12; i++) labels.add(start.plusMonths(i).format(MONTH_ONLY));
                highlight = today.getMonthValue() - 1;
                note = "PHP · Monthly sales this year";
            }
        }

        double[] values = new double[labels.size()];
        for (Sale s : sales) {
            int idx = bucketIndex(s.time(), start);
            if (idx >= 0 && idx < values.length) values[idx] += s.total().doubleValue();
        }

        double sum = 0;
        for (double v : values) sum += v;

        ChartUtil.fillBars(perfChart, labels, values, Math.max(0, Math.min(highlight, values.length - 1)));
        perfScaleLabel.setText(note);
        perfSubtitle.setText(peso(sum) + " total");
    }

    /** Which bar of the performance chart a sale belongs to (-1 = none). */
    private int bucketIndex(LocalDateTime t, LocalDate start) {
        LocalDate d = t.toLocalDate();
        return switch (period) {
            case DAILY -> (t.getHour() >= START_HOUR && t.getHour() <= END_HOUR) ? t.getHour() - START_HOUR : -1;
            case WEEKLY -> (int) ChronoUnit.DAYS.between(start, d);
            case MONTHLY -> d.getDayOfMonth() - 1;
            case QUARTERLY -> (int) ChronoUnit.MONTHS.between(start, d.withDayOfMonth(1));
            case YEARLY -> d.getMonthValue() - 1;
        };
    }

    // =====================================================================
    //  HOURLY CHART (sales by hour of day, summed over the period)
    // =====================================================================

    private void loadHourly(List<Sale> sales) {
        List<String> labels = new ArrayList<>();
        double[] values = new double[END_HOUR - START_HOUR + 1];

        for (int h = START_HOUR; h <= END_HOUR; h++) labels.add(hourLabel(h));

        double sum = 0;
        int peak = 0;
        for (Sale s : sales) {
            int h = s.time().getHour();
            if (h >= START_HOUR && h <= END_HOUR) {
                values[h - START_HOUR] += s.total().doubleValue();
                sum += s.total().doubleValue();
            }
        }
        for (int i = 1; i < values.length; i++) if (values[i] > values[peak]) peak = i;

        hourlyTitle.setText("Hourly sales · " + periodName(period));
        hourlyNote.setText(period == Period.DAILY ? "PHP · Sales per hour today"
                : "PHP · Sales per hour of day, all days combined");

        ChartUtil.fillBars(hourlyChart, labels, values, peak);
        hourlyFooter.setText(sum > 0
                ? "Peak hour " + hourLabel(START_HOUR + peak) + " • " + peso(sum) + " total"
                : "No sales in this period");
    }

    // =====================================================================
    //  TREND CHART (last N periods, ending with the current one)
    // =====================================================================

    private void loadTrend(List<Sale> all, LocalDate currentStart) {
        int n = switch (period) {
            case DAILY -> 7;
            case WEEKLY -> 8;
            case MONTHLY -> 6;
            case QUARTERLY -> 4;
            case YEARLY -> 5;
        };

        LocalDate[] starts = new LocalDate[n];
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate ps = shiftBack(period, currentStart, n - 1 - i);
            starts[i] = ps;
            labels.add(switch (period) {
                case DAILY, WEEKLY -> ps.format(DAY_MONTH);
                case MONTHLY -> ps.format(MONTH_ONLY);
                case QUARTERLY -> "Q" + ((ps.getMonthValue() - 1) / 3 + 1) + " " + ps.getYear();
                case YEARLY -> String.valueOf(ps.getYear());
            });
        }

        double[] values = new double[n];
        LocalDate windowEnd = periodEnd(period, currentStart);
        for (Sale s : all) {
            if (!inRange(s, starts[0], windowEnd)) continue;
            LocalDate d = s.time().toLocalDate();
            for (int i = n - 1; i >= 0; i--) {
                if (!d.isBefore(starts[i])) {
                    values[i] += s.total().doubleValue();
                    break;
                }
            }
        }

        double sum = 0;
        for (double v : values) sum += v;

        String unit = switch (period) {
            case DAILY -> "Last 7 days";
            case WEEKLY -> "Last 8 weeks";
            case MONTHLY -> "Last 6 months";
            case QUARTERLY -> "Last 4 quarters";
            case YEARLY -> "Last 5 years";
        };

        trendTitle.setText("Trend · " + unit);
        trendNote.setText("PHP · " + unit + ", current period highlighted");

        ChartUtil.fillBars(trendChart, labels, values, n - 1);
        trendFooter.setText(unit + " • " + peso(sum) + " total");
    }

    private static String peso(double amount) {
        return String.format("₱%,.0f", amount);
    }
}