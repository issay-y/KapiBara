package com.cafe.kapibara.kapibara.controller.main.admin.home;

import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.SaleLine;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The four lower cards of the admin Home page:
 * Sales Revenue (last 7 days), Items by Category, Best Selling Items, Lowest-Stock Items.
 * Styled with the "hl-" classes (see HomeLowerSection.css additions) on top of AdminShell.css.
 */
public final class HomeLowerSection {

    private static final String[] DONUT_COLORS = {
            "#1B4D2E", "#2563EB", "#F59E0B", "#E11D48", "#10B981", "#8B5CF6", "#64748B"
    };
    private static final String[] BAR_COLORS = {
            "#4E2216", "#FF8732", "#D42A0B", "#8A3B1F", "#C77D00"
    };

    private final CashierStore store;
    private final Runnable openSales;

    public HomeLowerSection(CashierStore store, Runnable openSales) {
        this.store = store;
        this.openSales = openSales;
    }

    public VBox build() {
        GridPane top = row(64, 36, revenueCard(), categoryCard());
        GridPane bottom = row(46, 54, bestSellersCard(), lowStockCard());
        VBox root = new VBox(12, top, bottom);
        root.setFillWidth(true);
        return root;
    }

    // =====================================================================
    //  Layout helpers
    // =====================================================================

    private static GridPane row(double leftPct, double rightPct, VBox left, VBox right) {
        GridPane g = new GridPane();
        g.setHgap(12);
        ColumnConstraints a = new ColumnConstraints();
        a.setPercentWidth(leftPct);
        ColumnConstraints b = new ColumnConstraints();
        b.setPercentWidth(rightPct);
        g.getColumnConstraints().addAll(a, b);
        left.setMaxWidth(Double.MAX_VALUE);
        right.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(left, Priority.ALWAYS);
        GridPane.setHgrow(right, Priority.ALWAYS);
        GridPane.setFillHeight(left, true);
        GridPane.setFillHeight(right, true);
        g.add(left, 0, 0);
        g.add(right, 1, 0);
        return g;
    }

    private static VBox card(String title, String sub, javafx.scene.Node badge) {
        Label t = new Label(title);
        t.getStyleClass().add("adm-card-title");
        Label s = new Label(sub);
        s.getStyleClass().add("adm-card-sub");
        VBox titles = new VBox(2, t, s);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(titles, spacer);
        head.setAlignment(Pos.TOP_LEFT);
        if (badge != null) head.getChildren().add(badge);

        VBox card = new VBox(6, head);
        card.getStyleClass().addAll("adm-card", "hl-card");
        return card;
    }

    private static Label pill(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        return l;
    }

    private static String peso0(double v) { return String.format(Locale.US, "₱%,.0f", v); }
    private static String peso2(double v) { return String.format(Locale.US, "₱%,.2f", v); }

    // =====================================================================
    //  1) Sales Revenue (Last 7 Days)
    // =====================================================================

    private VBox revenueCard() {
        LocalDate today = LocalDate.now();
        Map<LocalDate, Double> perDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) perDay.put(today.minusDays(i), 0.0);

        for (Sale s : store.sales()) {
            LocalDate d = s.time().toLocalDate();
            if (perDay.containsKey(d)) perDay.merge(d, s.total().doubleValue(), Double::sum);
        }

        double total = perDay.values().stream().mapToDouble(Double::doubleValue).sum();
        double max = perDay.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);

        CategoryAxis x = new CategoryAxis();
        x.setTickLabelGap(8);
        NumberAxis y = new NumberAxis();
        double upper = Math.max(500, Math.ceil(max * 1.15 / 500.0) * 500.0);
        y.setAutoRanging(false);
        y.setLowerBound(0);
        y.setUpperBound(upper);
        y.setTickUnit(upper / 4.0);
        y.setMinorTickVisible(false);
        y.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number n) { return peso0(n.doubleValue()); }
            @Override public Number fromString(String s) { return 0; }
        });

        AreaChart<String, Number> chart = new AreaChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setMinHeight(190);
        chart.setPrefHeight(250);
        chart.getStyleClass().add("hl-rev-chart");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        int idx = 0;
        for (Map.Entry<LocalDate, Double> e : perDay.entrySet()) {
            String label = e.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            XYChart.Data<String, Number> data = new XYChart.Data<>(label, e.getValue());
            if (idx == 6) {
                data.nodeProperty().addListener((obs, old, node) -> {
                    if (node != null && !node.getStyleClass().contains("hl-today-dot")) {
                        node.getStyleClass().add("hl-today-dot");
                    }
                });
            }
            series.getData().add(data);
            idx++;
        }
        chart.getData().add(series);

        Button link = new Button("Sales overview →");
        link.getStyleClass().add("adm-link");
        link.setOnAction(e -> { if (openSales != null) openSales.run(); });

        HBox badge = new HBox(12, link, pill("7-Day Total: " + peso2(total), "adm-pill-green"));
        badge.setAlignment(Pos.CENTER_RIGHT);

        VBox card = card("Sales Revenue (Last 7 Days)",
                "Daily gross revenue with today's highlight.", badge);
        card.getChildren().add(chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return card;
    }

    // =====================================================================
    //  2) Items by Category (multi-colour donut)
    // =====================================================================

    private VBox categoryCard() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Product p : store.products()) counts.merge(p.getCategory(), 1, Integer::sum);
        int total = store.products().size();

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<String> names = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        int others = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i < 6) {
                names.add(sorted.get(i).getKey());
                values.add(sorted.get(i).getValue());
            } else {
                others += sorted.get(i).getValue();
            }
        }
        if (others > 0) {
            names.add("Others");
            values.add(others);
        }

        PieChart pie = new PieChart();
        pie.setLabelsVisible(false);
        pie.setLegendVisible(false);
        pie.setClockwise(true);
        pie.setStartAngle(90);
        pie.setAnimated(false);
        pie.setMinSize(200, 200);
        pie.setPrefSize(200, 200);
        pie.setMaxSize(200, 200);
        for (int i = 0; i < names.size(); i++) {
            PieChart.Data d = new PieChart.Data(names.get(i), values.get(i));
            final String color = DONUT_COLORS[i % DONUT_COLORS.length];
            d.nodeProperty().addListener((obs, old, node) -> {
                if (node != null) node.setStyle("-fx-pie-color: " + color + ";");
            });
            pie.getData().add(d);
        }

        Circle hole = new Circle(60);
        hole.getStyleClass().add("hl-donut-hole");
        Label big = new Label(String.valueOf(total));
        big.getStyleClass().add("hl-donut-total");
        Label small = new Label("Total Items");
        small.getStyleClass().add("adm-card-sub");
        VBox center = new VBox(0, big, small);
        center.setAlignment(Pos.CENTER);
        center.setMouseTransparent(true);

        StackPane donut = new StackPane(pie, hole, center);
        donut.setAlignment(Pos.CENTER);
        donut.setPadding(Insets.EMPTY);
        donut.setMinSize(200, 200);
        donut.setMaxSize(200, 200);
        hole.setMouseTransparent(true);

        FlowPane legend = new FlowPane(8, 10);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setPrefWrapLength(300);
        for (int i = 0; i < names.size(); i++) {
            Circle dot = new Circle(4, javafx.scene.paint.Color.web(DONUT_COLORS[i % DONUT_COLORS.length]));
            double pct = total == 0 ? 0 : values.get(i) * 100.0 / total;
            Label l = new Label(names.get(i) + ": " + values.get(i) + String.format(Locale.US, " (%.1f%%)", pct));
            l.getStyleClass().add("hl-chip-text");
            HBox chip = new HBox(6, dot, l);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("hl-chip");
            legend.getChildren().add(chip);
        }

        VBox card = card("Items by Category",
                "Top " + Math.min(6, sorted.size()) + " departments" + (others > 0 ? " + Others." : "."), null);
        HBox body = new HBox(16, donut, legend);
        body.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(legend, Priority.ALWAYS);
        card.getChildren().add(body);
        return card;
    }

    // =====================================================================
    //  3) Best Selling Items (last 7 days, top 5)
    // =====================================================================

    private VBox bestSellersCard() {
        LocalDate since = LocalDate.now().minusDays(6);

        Map<String, String> categoryByName = new LinkedHashMap<>();
        for (Product p : store.products()) categoryByName.put(p.getName(), p.getCategory());

        Map<String, Integer> sold = new LinkedHashMap<>();
        for (Sale s : store.sales()) {
            if (s.time().toLocalDate().isBefore(since)) continue;
            for (SaleLine l : s.lines()) sold.merge(l.name(), l.qty(), Integer::sum);
        }

        List<Map.Entry<String, Integer>> top = new ArrayList<>(sold.entrySet());
        top.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        if (top.size() > 5) top = top.subList(0, 5);

        VBox card = card("Best Selling Items", "Top 5 items by quantity sold in the last 7 days.",
                pill("Top Movers", "adm-pill-gold"));

        if (top.isEmpty()) {
            Label empty = new Label("No sales in the last 7 days yet.");
            empty.getStyleClass().add("adm-seller-label");
            card.getChildren().add(empty);
            return card;
        }

        int max = top.get(0).getValue();
        final double barArea = 175;
        HBox columns = new HBox(10);
        columns.setAlignment(Pos.BOTTOM_CENTER);

        for (int i = 0; i < top.size(); i++) {
            Map.Entry<String, Integer> e = top.get(i);

            Label qty = new Label(e.getValue() + " sold");
            qty.getStyleClass().add("hl-bar-qty");

            Region bar = new Region();
            bar.getStyleClass().add("hl-bar");
            bar.setStyle("-fx-background-color: " + BAR_COLORS[i % BAR_COLORS.length] + ";");
            bar.setPrefWidth(56);
            bar.setMaxWidth(56);
            bar.setMinHeight(8);
            bar.setPrefHeight(Math.max(8, barArea * e.getValue() / max));
            bar.setMaxHeight(Math.max(8, barArea * e.getValue() / max));

            VBox barBox = new VBox(3, qty, bar);
            barBox.setAlignment(Pos.BOTTOM_CENTER);
            barBox.setPrefHeight(barArea + 22);
            barBox.setMinHeight(barArea + 22);

            Label name = new Label(e.getKey());
            name.getStyleClass().add("hl-bar-name");
            name.setWrapText(true);
            name.setAlignment(Pos.TOP_CENTER);
            name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            name.setMaxWidth(130);
            Label cat = new Label(categoryByName.getOrDefault(e.getKey(), ""));
            cat.getStyleClass().add("adm-card-sub");

            VBox col = new VBox(3, barBox, name, cat);
            col.setAlignment(Pos.BOTTOM_CENTER);
            col.setMinWidth(0);
            HBox.setHgrow(col, Priority.ALWAYS);
            col.setMaxWidth(Double.MAX_VALUE);
            columns.getChildren().add(col);
        }
        card.getChildren().add(columns);
        return card;
    }

    // =====================================================================
    //  4) Lowest-Stock Items (closest to minimum level)
    // =====================================================================

    private VBox lowStockCard() {
        List<Product> list = new ArrayList<>();
        for (Product p : store.products()) if (p.getMinimum() > 0) list.add(p);
        list.sort(Comparator.comparingDouble(p -> p.getStock() / p.getMinimum()));
        if (list.size() > 5) list = list.subList(0, 5);

        VBox card = card("Lowest-Stock Items", "Top 5 items closest to running out of their minimum level.",
                pill("Low Stock Alert", "adm-pill-red"));

        if (list.isEmpty()) {
            Label empty = new Label("No stock minimums are set yet.");
            empty.getStyleClass().add("adm-seller-label");
            card.getChildren().add(empty);
            return card;
        }

        VBox rows = new VBox(8);
        for (Product p : list) {
            Label name = new Label(p.getName());
            name.getStyleClass().add("hl-stock-name");
            Label cat = new Label(p.getCategory());
            cat.getStyleClass().add("adm-card-sub");
            HBox left = new HBox(8, name, cat);   // name and category on one line
            left.setAlignment(Pos.BASELINE_LEFT);
            HBox.setHgrow(left, Priority.ALWAYS);
            left.setMaxWidth(Double.MAX_VALUE);

            Label status;
            if (!p.isAvailable()) status = pill("Out of Stock", "adm-pill-red");
            else if (p.isLow()) status = pill("Low Stock", "adm-pill-gold");
            else status = pill("In Stock", "adm-pill-green");

            Label value = new Label(p.getStock() + " / " + (int) Math.round(p.getMinimum()));
            value.getStyleClass().add("hl-stock-value");
            ProgressBar bar = new ProgressBar(Math.min(1.0, p.getStock() / p.getMinimum()));
            bar.getStyleClass().add("adm-seller-bar");
            bar.setPrefWidth(150);
            VBox right = new VBox(3, value, bar);
            right.setAlignment(Pos.CENTER_RIGHT);

            HBox row = new HBox(14, left, status, right);
            row.setAlignment(Pos.CENTER);
            row.getStyleClass().add("hl-stock-row");
            rows.getChildren().add(row);
        }
        card.getChildren().add(rows);
        return card;
    }
}