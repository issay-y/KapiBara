package com.cafe.kapibara.kapibara.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class WindowUtil {

    public enum Mode { WINDOWED, MAXIMIZED, FULLSCREEN }

    // ---- screens ----
    private static final String LOGIN_FXML   = "/com/cafe/kapibara/kapibara/controller/Id/LoginView.fxml";
    private static final String ADMIN_FXML   = "/com/cafe/kapibara/kapibara/controller/main/admin/AdminView.fxml";
    private static final String CASHIER_FXML = "/com/cafe/kapibara/kapibara/controller/main/cashier/CashierView.fxml";

    // ---- window states ----
    private static final Mode LOGIN_MODE   = Mode.WINDOWED;
    private static final Mode ADMIN_MODE   = Mode.FULLSCREEN;
    private static final Mode CASHIER_MODE = Mode.FULLSCREEN;

    private static final double LOGIN_WIDTH  = 1280;
    private static final double LOGIN_HEIGHT = 720;

    private WindowUtil() {}

    public static <T> T showLogin(Stage stage) {
        return switchTo(stage, LOGIN_FXML, "KapiBara - Login", LOGIN_MODE, LOGIN_WIDTH, LOGIN_HEIGHT);
    }

    public static <T> T showAdmin(Stage stage) {
        return switchTo(stage, ADMIN_FXML, "KapiBara - Admin", ADMIN_MODE, 1280, 800);
    }

    public static <T> T showCashier(Stage stage) {
        return switchTo(stage, CASHIER_FXML, "KapiBara - Cashier", CASHIER_MODE, 1280, 800);
    }

    public static Stage stageOf(Node node) {
        return (Stage) node.getScene().getWindow();
    }

    public static <T> T switchTo(Stage stage, String fxmlPath, String title,
                                 Mode mode, double width, double height) {
        FXMLLoader loader = new FXMLLoader(WindowUtil.class.getResource(fxmlPath));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            AlertsUtil.error(stage, "Unable to open screen", "Could not load: " + fxmlPath);
            return null;
        }

        stage.setFullScreen(false);
        stage.setMaximized(false);

        stage.setTitle(title);
        stage.setScene(new Scene(root));

        switch (mode) {
            case WINDOWED -> {
                stage.setMinWidth(960);
                stage.setMinHeight(600);
                stage.setWidth(width);
                stage.setHeight(height);
                stage.centerOnScreen();
            }
            case MAXIMIZED -> {
                stage.setMinWidth(1000);
                stage.setMinHeight(640);
                stage.setMaximized(true);
            }
            case FULLSCREEN -> {
                stage.setMinWidth(1000);
                stage.setMinHeight(640);
                stage.setFullScreen(true);
            }
        }

        if (!stage.isShowing()) stage.show();
        return loader.getController();
    }
}