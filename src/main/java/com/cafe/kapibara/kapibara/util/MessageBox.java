package com.cafe.kapibara.kapibara.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * The app's message box: a rounded card with a serif title, a short message, and a
 * Cancel / red action button, like the "Delete this item?" box.
 * Styled by css/MessageBox.css (loaded here, so any screen can call it).
 */
public final class MessageBox {

    private static final String CSS = "/com/cafe/kapibara/kapibara/css/MessageBox.css";

    private MessageBox() { }

    /**
     * Two-button question. Returns true when the person picks the red confirm button.
     * With danger = true, Enter and the first focus go to Cancel so a stray key press can't
     * delete or discard anything.
     */
    public static boolean confirm(Node owner, String title, String message,
                                  String confirmText, String cancelText, boolean danger) {
        return show(owner, title, message, confirmText, cancelText, danger);
    }

    /** One-button notice. */
    public static void info(Node owner, String title, String message) {
        show(owner, title, message, "OK", null, false);
    }

    /** Placeholder for buttons whose screen doesn't exist yet. */
    public static void comingSoon(Node owner, String feature) {
        info(owner, feature, feature + " isn't available in this build yet.");
    }

    private static boolean show(Node owner, String title, String message,
                                String confirmText, String cancelText, boolean danger) {
        boolean[] result = {false};

        Window window = (owner == null || owner.getScene() == null) ? null : owner.getScene().getWindow();
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        if (window != null) {
            stage.initOwner(window);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("msg-title");
        titleLabel.setWrapText(true);

        Label textLabel = new Label(message);
        textLabel.getStyleClass().add("msg-text");
        textLabel.setWrapText(true);

        Button confirmBtn = new Button(confirmText);
        confirmBtn.getStyleClass().add("msg-btn-confirm");
        confirmBtn.setOnAction(e -> {
            result[0] = true;
            stage.close();
        });

        HBox buttons = new HBox(12);
        buttons.getStyleClass().add("msg-buttons");
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button focusTarget;
        if (cancelText != null) {
            Button cancelBtn = new Button(cancelText);
            cancelBtn.getStyleClass().add("msg-btn-cancel");
            cancelBtn.setCancelButton(true);                 // Esc
            cancelBtn.setOnAction(e -> stage.close());
            buttons.getChildren().addAll(cancelBtn, confirmBtn);

            focusTarget = danger ? cancelBtn : confirmBtn;
        } else {
            confirmBtn.setCancelButton(true);                // Esc closes a notice too
            buttons.getChildren().add(confirmBtn);
            focusTarget = confirmBtn;
        }
        focusTarget.setDefaultButton(true);                  // Enter

        VBox card = new VBox(12, titleLabel, textLabel, buttons);
        card.getStyleClass().add("msg-card");
        card.setPrefWidth(440);
        card.setMaxWidth(440);
        VBox.setMargin(buttons, new Insets(14, 0, 0, 0));

        StackPane root = new StackPane(card);
        root.getStyleClass().add("msg-root");

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(MessageBox.class.getResource(CSS).toExternalForm());
        stage.setScene(scene);

        stage.setOnShown(e -> {
            if (window != null) {
                stage.setX(window.getX() + (window.getWidth() - stage.getWidth()) / 2);
                stage.setY(window.getY() + (window.getHeight() - stage.getHeight()) / 2);
            }
            Platform.runLater(focusTarget::requestFocus);
        });

        stage.showAndWait();
        return result[0];
    }
}