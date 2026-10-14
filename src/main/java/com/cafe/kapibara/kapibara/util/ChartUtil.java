package com.cafe.kapibara.kapibara.util;

import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;

import java.util.List;

public final class ChartUtil {

    private ChartUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Fills a bar chart. Each category label shows the value underneath it, and the bar at
     * highlightIndex (usually "now") gets the "bar-highlight" style class (red in AdminShell.css).
     */
    public static void fillBars(BarChart<String, Number> chart, List<String> labels,
                                double[] values, int highlightIndex) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < values.length; i++) {
            String category = labels.get(i) + "\n" + String.format("%,.0f", values[i]);
            XYChart.Data<String, Number> data = new XYChart.Data<>(category, values[i]);
            if (i == highlightIndex) {
                data.nodeProperty().addListener((obs, old, node) -> {
                    if (node != null && !node.getStyleClass().contains("bar-highlight")) {
                        node.getStyleClass().add("bar-highlight");
                    }
                });
            }
            series.getData().add(data);
        }
        chart.getData().setAll(series);
    }

    public static double sum(double[] values) {
        double total = 0;
        for (double v : values) total += v;
        return total;
    }
}