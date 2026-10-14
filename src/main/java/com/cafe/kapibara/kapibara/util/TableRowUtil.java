package com.cafe.kapibara.kapibara.util;

import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;


public final class TableRowUtil {

    private TableRowUtil() {}

    public static void polish(GridPane grid, int columns) {
        // remove lines from a previous render
        grid.getChildren().removeIf(n -> n.getStyleClass().contains("adm-row-line"));

        int maxRow = 0;
        for (Node n : grid.getChildren()) {
            GridPane.setFillHeight(n, true);
            GridPane.setValignment(n, VPos.CENTER);
            if (n instanceof Region r) {
                r.setMaxHeight(Double.MAX_VALUE);
            }
            Integer row = GridPane.getRowIndex(n);
            maxRow = Math.max(maxRow, row == null ? 0 : row);
        }

        for (int r = 1; r < maxRow; r++) {
            Region line = new Region();
            line.getStyleClass().add("adm-row-line");
            line.setMinHeight(1);
            line.setPrefHeight(1);
            line.setMaxHeight(1);
            line.setMouseTransparent(true);
            GridPane.setRowIndex(line, r);
            GridPane.setColumnIndex(line, 0);
            GridPane.setColumnSpan(line, columns);
            GridPane.setValignment(line, VPos.BOTTOM);
            GridPane.setFillWidth(line, true);
            grid.getChildren().add(line);
        }
    }
}