package com.cafe.kapibara.kapibara.util;

import com.cafe.kapibara.kapibara.controller.util.AlertController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Effect;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;

public final class AlertsUtil {

    public enum Type { INFORMATION, CONFIRMATION, WARNING, ERROR }

    private static final String VIEW_PATH = "/com/cafe/kapibara/kapibara/controller/util/AlertView.fxml";

    private AlertsUtil() {}

    public static void info(Window owner, String title, String message) {
        show(owner, Type.INFORMATION, title, message, "OK", null);
    }

    public static void warning(Window owner, String title, String message) {
        show(owner, Type.WARNING, title, message, "OK", null);
    }

    public static void error(Window owner, String title, String message) {
        show(owner, Type.ERROR, title, message, "OK", null);
    }

    public static boolean confirm(Window owner, String title, String message) {
        return show(owner, Type.CONFIRMATION, title, message, "Confirm", "Cancel");
    }

    public static boolean confirm(Window owner, Type type, String title, String message, String confirmText) {
        return show(owner, type, title, message, confirmText, "Cancel");
    }

    public static void info(Node owner, String title, String message)    { info(windowOf(owner), title, message); }
    public static void warning(Node owner, String title, String message) { warning(windowOf(owner), title, message); }
    public static void error(Node owner, String title, String message)   { error(windowOf(owner), title, message); }

    public static boolean confirm(Node owner, String title, String message) {
        return confirm(windowOf(owner), title, message);
    }

    public static boolean confirm(Node owner, Type type, String title, String message, String confirmText) {
        return confirm(windowOf(owner), type, title, message, confirmText);
    }

    private static Window windowOf(Node node) {
        return (node != null && node.getScene() != null) ? node.getScene().getWindow() : null;
    }

    private static boolean show(Window owner, Type type, String title, String message,
                                String confirmText, String cancelText) {
        Parent root;
        AlertController controller;
        try {
            FXMLLoader loader = new FXMLLoader(AlertsUtil.class.getResource(VIEW_PATH));
            root = loader.load();
            controller = loader.getController();
        } catch (IOException | RuntimeException e) {
            System.err.println("AlertUtil: could not load " + VIEW_PATH);
            e.printStackTrace();
            return false;
        }

        controller.setup(type, title, message, confirmText, cancelText);

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();   // treated as cancel
        });
        stage.setScene(scene);

        // Center over the owner window
        stage.setOnShown(e -> {
            if (owner != null) {
                stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
                stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
            }
            controller.focusConfirm();
        });

        // Dim the window behind the dialog
        Node ownerRoot = (owner != null && owner.getScene() != null) ? owner.getScene().getRoot() : null;
        Effect previousEffect = null;
        if (ownerRoot != null) {
            previousEffect = ownerRoot.getEffect();
            ColorAdjust dim = new ColorAdjust();
            dim.setBrightness(-0.45);
            ownerRoot.setEffect(dim);
        }

        try {
            stage.showAndWait();
        } finally {
            if (ownerRoot != null) ownerRoot.setEffect(previousEffect);
        }

        return controller.isConfirmed();
    }
}