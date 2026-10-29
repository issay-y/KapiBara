package com.cafe.kapibara.kapibara.controller.main.admin.sale;

import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.SaleLine;
import com.cafe.kapibara.kapibara.util.ChartUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Sales overview page. A From / To calendar (default: 1st of this month to today)
 * drives every card, chart and list on the page. The old Daily..Yearly buttons are hidden.
 */
public class SaleController implements Initializable, AdminShell.Aware {

    private enum Grain { HOURLY, DAILY, WEEKLY, MONTHLY }

    private static final Locale EN = Locale.ENGLISH;
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MMM d", EN);
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MMM d, yyyy", EN);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd MMM", EN);
    private static final DateTimeFormatter MONTH_ONLY = DateTimeFormatter.ofPattern("MMM", EN);
    private static final DateTimeFormatter MONTH_YEAR_SHORT = DateTimeFormatter.ofPattern("MMM yy", EN);
    private static final DateTimeFormatter WEEKDAY_DAY = DateTimeFormatter.ofPattern("EEE dd", EN);

    private static final int START_HOUR = 7;   // default hour window; widens if sales fall outside it
    private static final int END_HOUR = 18;

    // old period selector (kept so the FXML still loads; the buttons are hidden)
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

    // calendar controls (built in code)
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private boolean controlsMounted = false;
    private boolean adjusting = false;

    private LocalDate from;
    private LocalDate to;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // never allow "nothing selected" on the (hidden) old toggle group
        periodGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });

        LocalDate today = LocalDate.now();
        from = today.withDayOfMonth(1);
        to = today;
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;

        try {
            store.refreshSalesHistory();
        } catch (SQLException e) {
            System.err.println("Could not load sales history: " + e.getMessage());
        }

        mountDateControls();
        loadDashboard();
    }

    /** Kept because the FXML references it; the buttons are hidden so it is never triggered. */
    @FXML
    private void handlePeriodChange(ActionEvent event) { }

    // =====================================================================
    //  CALENDAR CONTROLS
    // =====================================================================

    private void mountDateControls() {
        if (controlsMounted) return;
        controlsMounted = true;

        // hide the old Daily..Yearly buttons
        for (ToggleButton b : new ToggleButton[]{dailyBtn, weeklyBtn, monthlyBtn, quarterlyBtn, yearlyBtn}) {
            b.setVisible(false);
            b.setManaged(false);
        }

        setUpPicker(fromPicker, from);
        setUpPicker(toPicker, to);

        fromPicker.valueProperty().addListener((o, a, b) -> {
            if (adjusting || b == null) return;
            from = b;
            if (to.isBefore(from)) { adjusting = true; to = from; toPicker.setValue(to); adjusting = false; }
            loadDashboard();
        });
        toPicker.valueProperty().addListener((o, a, b) -> {
            if (adjusting || b == null) return;
            to = b;
            if (to.isBefore(from)) { adjusting = true; from = to; fromPicker.setValue(from); adjusting = false; }
            loadDashboard();
        });

        HBox controls = new HBox(8, small("From"), fromPicker, small("To"), toPicker);
        controls.setAlignment(Pos.CENTER_RIGHT);

        Node host = dailyBtn.getParent();
        if (host instanceof Pane pane) {
            pane.getChildren().add(controls);
        } else {
            System.err.println("SaleController: could not find where to put the calendar controls.");
        }
    }

    private static Label small(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("adm-filter-label");
        return l;
    }

    private static void setUpPicker(DatePicker picker, LocalDate value) {
        picker.setValue(value);
        picker.setPrefWidth(160);
        picker.getStyleClass().add("sl-date");
        picker.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : d.format(MDY); }
            @Override public LocalDate fromString(String s) {
                try { return (s == null || s.isBlank()) ? null : LocalDate.parse(s.trim(), MDY); }
                catch (DateTimeParseException e) { return null; }
            }
        });
        // no future dates
        picker.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null && item.isAfter(LocalDate.now())) setDisable(true);
            }
        });
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================

    private static boolean between(Sale s, LocalDate a, LocalDate b) {
        LocalDate d = s.time().toLocalDate();
        return !d.isBefore(a) && !d.isAfter(b);
    }

    private static String rangeText(LocalDate a, LocalDate b) {
        if (a.equals(b)) return a.format(MDY);
        if (a.getYear() == b.getYear()) return a.format(MD) + " – " + b.format(MDY);
        return a.format(MDY) + " – " + b.format(MDY);
    }

    private static String hourLabel(int h) {
        return h < 12 ? h + " AM" : (h == 12 ? "12 PM" : (h - 12) + " PM");
    }

    private static Grain grainFor(long days) {
        if (days <= 1) return Grain.HOURLY;
        if (days <= 31) return Grain.DAILY;
        if (days <= 186) return Grain.WEEKLY;
        return Grain.MONTHLY;
    }

    /** Hour window: 7 AM - 6 PM by default, widened when sales happen outside it. */
    private static int[] hourBounds(List<Sale> sales) {
        int lo = START_HOUR, hi = END_HOUR;
        for (Sale s : sales) {
            int h = s.time().getHour();
            if (h < lo) lo = h;
            if (h > hi) hi = h;
        }
        return new int[]{lo, hi};
    }

    private static String signedPct(double pct) {
        return String.format(EN, "%s%.1f%%", pct >= 0 ? "+" : "−", Math.abs(pct));
    }

    // =====================================================================
    //  DASHBOARD
    // =====================================================================

    private void loadDashboard() {
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevFrom = from.minusDays(days);
        LocalDate prevTo = from.minusDays(1);

        List<Sale> all = store.sales();
        List<Sale> current = new ArrayList<>();
        double curTotal = 0, prevTotal = 0;
        int prevTx = 0;

        for (Sale s : all) {
            if (between(s, from, to)) {
                current.add(s);
                curTotal += s.total().doubleValue();
            } else if (between(s, prevFrom, prevTo)) {
                prevTotal += s.total().doubleValue();
                prevTx++;
            }
        }
        int curTx = current.size();
        String range = rangeText(from, to);

        rangeLabel.setText(range + " · " + days + (days == 1 ? " day" : " days"));

        // KPI 1: total for the range, with change vs the previous equal-length range
        salesLabel.setText("Total Sales");
        salesValue.setText(peso(curTotal));
        String vs = prevTotal > 0 ? " · " + signedPct((curTotal - prevTotal) / prevTotal * 100.0) + " vs previous" : "";
        salesSub.setText(curTx + (curTx == 1 ? " transaction" : " transactions") + vs);

        // KPI 2: previous period
        prevLabel.setText("Previous Period");
        prevValue.setText(peso(prevTotal));
        prevSub.setText(rangeText(prevFrom, prevTo) + " · " + prevTx + (prevTx == 1 ? " tx" : " tx"));

        // KPI 3 + 4
        txLabel.setText("Total Transactions");
        txValue.setText(String.valueOf(curTx));
        txSub.setText("Selected range · All complete");

        avgLabel.setText("Average Transaction");
        avgValue.setText(curTx > 0 ? peso(curTotal / curTx) : "₱0");
        avgSub.setText(curTx > 0 ? "Sales ÷ " + curTx + " orders" : "No orders in this range");

        topLabel.setText("Most Sold Product");

        loadBestSellers(current, range);
        loadPerformance(current, today, days);
        loadHourly(current, days);
        loadTrend(all);
    }

    // =====================================================================
    //  BEST SELLERS
    // =====================================================================

    private void loadBestSellers(List<Sale> sales, String range) {
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

        bestSellersTitle.setText("Best sellers");
        bestSellersBox.getChildren().clear();

        if (sorted.isEmpty()) {
            topProductValue.setText("No sales yet");
            topProductSub.setText("Nothing sold in this range");

            Label empty = new Label("No items sold in this range.");
            empty.getStyleClass().add("adm-seller-label");
            bestSellersBox.getChildren().add(empty);
            bestSellersFooter.setText("0 items sold · " + range);
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
        bestSellersFooter.setText(sorted.size() + " unique products sold • Other products: " + others);
    }

    private static class ProductStats {
        final String name;
        int sold = 0;
        double revenue = 0;
        ProductStats(String name) { this.name = name; }
    }

    // =====================================================================
    //  SALES PERFORMANCE (breakdown across the selected range)
    // =====================================================================

    private void loadPerformance(List<Sale> sales, LocalDate today, long days) {
        Grain grain = grainFor(days);
        int[] hb = hourBounds(sales);
        List<String> labels = new ArrayList<>();
        String note;

        switch (grain) {
            case HOURLY -> {
                for (int h = hb[0]; h <= hb[1]; h++) labels.add(hourLabel(h));
                note = "PHP · Hourly sales";
            }
            case DAILY -> {
                boolean sameMonth = from.getMonth() == to.getMonth() && from.getYear() == to.getYear();
                for (int i = 0; i < days; i++) {
                    LocalDate d = from.plusDays(i);
                    labels.add(days <= 7 ? d.format(WEEKDAY_DAY) : (sameMonth ? String.format("%02d", d.getDayOfMonth()) : d.format(MD)));
                }
                note = "PHP · Daily sales";
            }
            case WEEKLY -> {
                int weeks = (int) Math.ceil(days / 7.0);
                for (int i = 0; i < weeks; i++) labels.add(from.plusWeeks(i).format(DAY_MONTH));
                note = "PHP · Weekly sales (weeks start " + from.format(MD) + ")";
            }
            default -> {
                int months = (int) ChronoUnit.MONTHS.between(from.withDayOfMonth(1), to.withDayOfMonth(1)) + 1;
                boolean multiYear = from.getYear() != to.getYear();
                for (int i = 0; i < months; i++) {
                    LocalDate m = from.withDayOfMonth(1).plusMonths(i);
                    labels.add(m.format(multiYear ? MONTH_YEAR_SHORT : MONTH_ONLY));
                }
                note = "PHP · Monthly sales";
            }
        }

        double[] values = new double[labels.size()];
        double sum = 0;
        for (Sale s : sales) {
            int idx = bucketIndex(s, grain, hb[0]);
            if (idx >= 0 && idx < values.length) values[idx] += s.total().doubleValue();
            sum += s.total().doubleValue();
        }

        // highlight the bucket that contains today, otherwise the last bar
        int highlight = values.length - 1;
        if (!today.isBefore(from) && !today.isAfter(to)) {
            Sale probe = null;
            int idx = switch (grain) {
                case HOURLY -> java.time.LocalTime.now().getHour() - hb[0];
                case DAILY -> (int) ChronoUnit.DAYS.between(from, today);
                case WEEKLY -> (int) (ChronoUnit.DAYS.between(from, today) / 7);
                case MONTHLY -> (int) ChronoUnit.MONTHS.between(from.withDayOfMonth(1), today.withDayOfMonth(1));
            };
            if (idx >= 0 && idx < values.length) highlight = idx;
        }

        ChartUtil.fillBars(perfChart, labels, values, Math.max(0, highlight));
        perfScaleLabel.setText(note);
        perfSubtitle.setText(peso(sum) + " total · " + rangeText(from, to));
    }

    /** Which bar of the performance chart a sale belongs to (-1 = none). */
    private int bucketIndex(Sale s, Grain grain, int firstHour) {
        LocalDate d = s.time().toLocalDate();
        return switch (grain) {
            case HOURLY -> s.time().getHour() - firstHour;
            case DAILY -> (int) ChronoUnit.DAYS.between(from, d);
            case WEEKLY -> (int) (ChronoUnit.DAYS.between(from, d) / 7);
            case MONTHLY -> (int) ChronoUnit.MONTHS.between(from.withDayOfMonth(1), d.withDayOfMonth(1));
        };
    }


    private void loadHourly(List<Sale> sales, long days) {
        int[] hb = hourBounds(sales);
        List<String> labels = new ArrayList<>();
        double[] values = new double[hb[1] - hb[0] + 1];

        for (int h = hb[0]; h <= hb[1]; h++) labels.add(hourLabel(h));

        double sum = 0;
        int peak = 0;
        for (Sale s : sales) {
            values[s.time().getHour() - hb[0]] += s.total().doubleValue();
            sum += s.total().doubleValue();
        }
        for (int i = 1; i < values.length; i++) if (values[i] > values[peak]) peak = i;

        hourlyTitle.setText("Hourly sales");
        hourlyNote.setText(days <= 1 ? "PHP · Sales per hour" : "PHP · Sales per hour of day, all days combined");

        ChartUtil.fillBars(hourlyChart, labels, values, peak);
        hourlyFooter.setText(sum > 0
                ? "Peak hour " + hourLabel(hb[0] + peak) + " • " + peso(sum) + " total"
                : "No sales in this range");
    }


    private void loadTrend(List<Sale> all) {
        int n = 7;
        LocalDate first = to.minusDays(n - 1);
        List<String> labels = new ArrayList<>();
        double[] values = new double[n];

        for (int i = 0; i < n; i++) labels.add(first.plusDays(i).format(DAY_MONTH));

        double sum = 0;
        for (Sale s : all) {
            if (!between(s, first, to)) continue;
            int idx = (int) ChronoUnit.DAYS.between(first, s.time().toLocalDate());
            values[idx] += s.total().doubleValue();
            sum += s.total().doubleValue();
        }

        trendTitle.setText("Trend · 7 days to " + to.format(MD));
        trendNote.setText("PHP · " + rangeText(first, to) + ", last day highlighted");

        ChartUtil.fillBars(trendChart, labels, values, n - 1);
        trendFooter.setText("Last 7 days • " + peso(sum) + " total");
    }

    private static String peso(double amount) {
        return String.format("₱%,.0f", amount);
    }
}