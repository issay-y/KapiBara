package com.cafe.kapibara.kapibara.util;

import com.cafe.kapibara.kapibara.controller.main.admin.settings.SettingsService;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.net.URL;

/**
 * Call VoidGuard.authorize(node) before voiding a transaction.
 * Returns true if the void may go ahead (PIN off, or the right PIN was entered).
 */
public final class VoidGuard {

    private static final int MAX_ATTEMPTS = 3;
    private static final String CSS = "/com/cafe/kapibara/kapibara/css/main/admin/AdminShell.css";

    private VoidGuard() {}

    public static boolean authorize(Node owner) {
        if (!SettingsService.isVoidPinRequired()) return true;
        if (!SettingsService.hasVoidPin()) return true;   // switched on but no PIN saved: don't lock everyone out

        boolean[] allowed = {false};
        boolean[] blocked = {false};
        showDialog(owner, allowed, blocked);

        if (blocked[0]) {
            MessageBox.info(owner, "Void blocked", "Too many wrong PIN attempts. Nothing was voided.");
        }
        return allowed[0];
    }

    private static void showDialog(Node owner, boolean[] allowed, boolean[] blocked) {
        Window win = (owner != null && owner.getScene() != null) ? owner.getScene().getWindow() : null;

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (win != null) stage.initOwner(win);

        // ---- content ----
        Label icon = new Label("🔒");
        icon.getStyleClass().add("pin-icon");
        StackPane iconWrap = new StackPane(icon);
        iconWrap.getStyleClass().add("pin-icon-wrap");

        Label title = new Label("Void authorization");
        title.getStyleClass().add("pin-title");

        Label note = new Label("Enter the PIN to void this transaction.");
        note.getStyleClass().add("pin-note");
        note.setWrapText(true);
        note.setAlignment(Pos.CENTER);
        note.setMaxWidth(Double.MAX_VALUE);

        PasswordField field = new PasswordField();
        field.setPromptText("Enter PIN");
        field.getStyleClass().addAll("adm-field", "pin-field");
        field.setMaxWidth(Double.MAX_VALUE);
        // digits only, max 6
        field.textProperty().addListener((obs, old, text) -> {
            if (!text.matches("\\d{0,6}")) field.setText(old);
        });

        Label error = new Label();
        error.getStyleClass().add("adm-form-error");
        error.setWrapText(true);
        error.setAlignment(Pos.CENTER);
        error.setMaxWidth(Double.MAX_VALUE);
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        error.visibleProperty().bind(error.textProperty().isNotEmpty());

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("adm-page-btn", "pin-btn");
        cancel.setCancelButton(true);
        cancel.setFocusTraversable(false);
        cancel.setMaxWidth(Double.MAX_VALUE);

        Button confirm = new Button("Confirm");
        confirm.getStyleClass().addAll("adm-primary-btn", "pin-btn");
        confirm.setDefaultButton(true);
        confirm.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(cancel, Priority.ALWAYS);
        HBox.setHgrow(confirm, Priority.ALWAYS);
        HBox buttons = new HBox(12, cancel, confirm);

        VBox card = new VBox(12, iconWrap, title, note, field, error, buttons);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("pin-card");
        card.setPrefWidth(380);
        card.setMaxWidth(380);
        VBox.setMargin(field, new javafx.geometry.Insets(8, 0, 0, 0));
        VBox.setMargin(buttons, new javafx.geometry.Insets(8, 0, 0, 0));

        // "adm-root" so the -adm-* colours resolve; the padding leaves room for the shadow
        StackPane root = new StackPane(card);
        root.getStyleClass().addAll("adm-root", "pin-root");

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        URL css = VoidGuard.class.getResource(CSS);
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);

        // ---- behaviour ----
        int[] attempts = {0};

        Runnable submit = () -> {
            if (SettingsService.verifyVoidPin(field.getText())) {
                allowed[0] = true;
                stage.close();
                return;
            }
            attempts[0]++;
            int left = MAX_ATTEMPTS - attempts[0];
            if (left <= 0) {
                blocked[0] = true;
                stage.close();
                return;
            }
            error.setText("Wrong PIN. " + left + (left == 1 ? " attempt" : " attempts") + " left.");
            field.clear();
            field.requestFocus();
        };

        confirm.setOnAction(e -> submit.run());
        field.setOnAction(e -> submit.run());
        cancel.setOnAction(e -> stage.close());
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });

        stage.setOnShown(e -> {
            if (win != null) {
                stage.setX(win.getX() + (win.getWidth() - stage.getWidth()) / 2);
                stage.setY(win.getY() + (win.getHeight() - stage.getHeight()) / 2);
            }
            Platform.runLater(field::requestFocus);
        });

        stage.showAndWait();
    }
}