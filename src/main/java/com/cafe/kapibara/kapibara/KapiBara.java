package com.cafe.kapibara.kapibara;

import com.cafe.kapibara.kapibara.config.DatabaseConnector;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

public class KapiBara extends Application {
    @Override
    public void start(Stage stage) {
        warmUpDatabase();

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(KapiBara.class.getResource(
                    "/com/cafe/kapibara/kapibara/controller/Id/LoginView.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1280, 720);
            stage.setTitle("KapiBara Cafe");
            try (var iconStream = KapiBara.class.getResourceAsStream(
                    "/com/cafe/kapibara/kapibara/images/logo.png")) {
                if (iconStream != null) {
                    stage.getIcons().add(new javafx.scene.image.Image(iconStream));
                }
            } catch (Exception ignored) {
            }
            stage.setScene(scene);
            stage.setMinWidth(960);
            stage.setMinHeight(600);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            AlertsUtil.error((Window) null, "Startup Error",
                    "KapiBara could not load its main window:\n" + e.getMessage());
            javafx.application.Platform.exit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void warmUpDatabase() {
        Thread warmup = new Thread(() -> {
            try (var conn = DatabaseConnector.getConnection()) {
            } catch (Throwable t) {
                System.err.println("KapiBara: database warm-up failed: " + t.getMessage());
            }
        }, "db-warmup");
        warmup.setDaemon(true);
        warmup.start();
    }

    @Override
    public void stop() {
        DatabaseConnector.closePool();
    }
}