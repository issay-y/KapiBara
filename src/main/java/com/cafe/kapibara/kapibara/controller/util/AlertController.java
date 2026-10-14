package com.cafe.kapibara.kapibara.controller.util;

import com.cafe.kapibara.kapibara.util.AlertsUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class AlertController {

    @FXML private VBox card;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private HBox buttonBar;
    @FXML private Button cancelBtn;
    @FXML private Button confirmBtn;

    private boolean confirmed = false;

    public void setup(AlertsUtil.Type type, String title, String message,
                      String confirmText, String cancelText) {

        titleLabel.setText(title);
        messageLabel.setText(message);
        confirmBtn.setText(confirmText);

        boolean red = (type == AlertsUtil.Type.WARNING || type == AlertsUtil.Type.ERROR);
        card.getStyleClass().add(red ? "alert-red" : "alert-brown");

        if (cancelText == null) {
            cancelBtn.setVisible(false);
            cancelBtn.setManaged(false);
        } else {
            cancelBtn.setText(cancelText);
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void focusConfirm() {
        confirmBtn.requestFocus();
    }

    @FXML
    private void handleConfirm(ActionEvent event) {
        confirmed = true;
        close();
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        confirmed = false;
        close();
    }

    private void close() {
        ((Stage) card.getScene().getWindow()).close();
    }
}