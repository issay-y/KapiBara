package com.cafe.kapibara.kapibara.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

import java.net.URL;

/** Styled message boxes (confirm / info / warning) that match the admin screens. */
public final class DialogUtil {

    private static final String CSS = "/com/cafe/kapibara/kapibara/css/main/admin/AdminShell.css";

    private DialogUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Asks a yes/no question. The safe option (cancelText) has the focus, so pressing Enter
     * never confirms by accident.
     *
     * @return true only if the person clicked confirmText
     */
    public static boolean confirm(Node owner, String title, String message,
                                  String confirmText, String cancelText) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        ButtonType yes = new ButtonType(confirmText, ButtonBar.ButtonData.OTHER);
        ButtonType no = new ButtonType(cancelText, ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yes, no);
        prepare(alert, owner, title, message);

        DialogPane pane = alert.getDialogPane();
        pane.lookupButton(yes).getStyleClass().add("adm-dialog-danger");
        Platform.runLater(() -> pane.lookupButton(no).requestFocus());

        return alert.showAndWait().map(button -> button == yes).orElse(false);
    }

    public static void info(Node owner, String title, String message) {
        show(Alert.AlertType.INFORMATION, owner, title, message);
    }

    public static void warn(Node owner, String title, String message) {
        show(Alert.AlertType.WARNING, owner, title, message);
    }

    private static void show(Alert.AlertType type, Node owner, String title, String message) {
        Alert alert = new Alert(type);
        prepare(alert, owner, title, message);
        alert.showAndWait();
    }

    private static void prepare(Alert alert, Node owner, String title, String message) {
        alert.setTitle("KapiBara");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.setGraphic(null);

        if (owner != null && owner.getScene() != null) {
            alert.initOwner(owner.getScene().getWindow());
        }

        DialogPane pane = alert.getDialogPane();
        URL css = DialogUtil.class.getResource(CSS);
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("adm-dialog");
    }
}