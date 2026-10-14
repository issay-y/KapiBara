package com.cafe.kapibara.kapibara.controller.main.admin.inventory;

import com.cafe.kapibara.kapibara.DAO.InventoryDAO;
import com.cafe.kapibara.kapibara.model.inventory.InventoryCategory;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Consumer;

public final class CategoryManagerDialog {

    private interface Work<T> { T run() throws Exception; }

    private final InventoryDAO dao = new InventoryDAO();
    private final Runnable onChanged;
    private final Stage stage = new Stage();

    private final TextField nameField = new TextField();
    private final Label errorLabel = new Label();
    private final VBox rows = new VBox();
    private boolean changed;

    public static void show(Node owner, Runnable onChanged) {
        new CategoryManagerDialog(owner, onChanged).stage.show();
    }

    private CategoryManagerDialog(Node owner, Runnable onChanged) {
        this.onChanged = onChanged;

        Label title = new Label("Manage categories");
        title.getStyleClass().add("adm-card-title");
        Label note = new Label("Add a category, or delete one that no item uses.");
        note.getStyleClass().add("adm-form-note");

        nameField.setPromptText("New category name");
        nameField.getStyleClass().add("adm-field");
        HBox.setHgrow(nameField, Priority.ALWAYS);
        nameField.setOnAction(e -> handleAdd());

        Button addBtn = new Button("＋   Add");
        addBtn.getStyleClass().add("adm-primary-btn");
        addBtn.setMinHeight(52);
        addBtn.setOnAction(e -> handleAdd());

        HBox addRow = new HBox(12, nameField, addBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);

        errorLabel.getStyleClass().add("adm-form-error");
        errorLabel.setWrapText(true);
        clearError();

        rows.setFillWidth(true);
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("adm-scroll");
        VBox listCard = new VBox(scroll);
        listCard.getStyleClass().add("adm-card");
        listCard.setPadding(new Insets(6, 8, 6, 8));
        VBox.setVgrow(listCard, Priority.ALWAYS);

        Button done = new Button("Done");
        done.getStyleClass().add("adm-page-btn");
        done.setOnAction(e -> stage.close());
        HBox footer = new HBox(done);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(16, title, note, addRow, errorLabel, listCard, footer);
        root.getStyleClass().addAll("adm-root", "adm-page", "adm-lg");
        root.setPadding(new Insets(24));

        Scene scene = new Scene(root, 560, 640);
        if (owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
            stage.initOwner(owner.getScene().getWindow());
        }
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Manage categories");
        stage.setScene(scene);
        stage.setMinWidth(480);
        stage.setMinHeight(480);
        stage.setOnHidden(e -> {
            if (changed && onChanged != null) onChanged.run();
        });

        load();
    }

    // =====================================================================
    //  LOAD + RENDER
    // =====================================================================

    private void load() {
        async(dao::findCategoryUsage, this::renderRows);
    }

    private void renderRows(List<InventoryCategory> categories) {
        rows.getChildren().clear();

        if (categories.isEmpty()) {
            Label empty = new Label("No categories yet. Add one above.");
            empty.getStyleClass().add("adm-form-note");
            empty.setPadding(new Insets(18, 16, 18, 16));
            rows.getChildren().add(empty);
            return;
        }

        for (int i = 0; i < categories.size(); i++) {
            InventoryCategory c = categories.get(i);

            Label name = new Label(c.name());
            name.getStyleClass().add("adm-role-name");

            Label count = new Label(c.itemCount() + (c.itemCount() == 1 ? " item" : " items"));
            count.getStyleClass().add("adm-stock-name");

            Button delete = new Button("Delete");
            delete.getStyleClass().add("adm-link");
            delete.setOnAction(e -> handleDelete(c));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(14, name, spacer, count, delete);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(14, 16, 14, 16));
            rows.getChildren().add(row);

            if (i < categories.size() - 1) {
                Region line = new Region();
                line.getStyleClass().add("adm-row-line");
                line.setMinHeight(1);
                line.setPrefHeight(1);
                line.setMaxHeight(1);
                rows.getChildren().add(line);
            }
        }
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================

    private void handleAdd() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            showError("Please enter a category name.");
            return;
        }
        if (name.length() > 50) {
            showError("The category name can be at most 50 characters.");
            return;
        }
        clearError();

        async(() -> {
            if (dao.categoryNameExists(name)) return Boolean.FALSE;
            dao.insertCategory(name);
            return Boolean.TRUE;
        }, added -> {
            if (!added) {
                showError("A category named \"" + name + "\" already exists.");
                return;
            }
            changed = true;
            nameField.clear();
            load();
        });
    }

    private void handleDelete(InventoryCategory category) {
        Node window = stage.getScene().getRoot();

        if (category.itemCount() > 0) {
            AlertsUtil.warning(window, "Category in use",
                    category.name() + " is used by " + category.itemCount()
                            + (category.itemCount() == 1 ? " item" : " items")
                            + ".\nMove or delete those items first.");
            return;
        }

        boolean confirmed = AlertsUtil.confirm(window, AlertsUtil.Type.WARNING, "Delete this category?",
                "Delete " + category.name() + "?\nThis can't be undone.", "Delete");
        if (!confirmed) return;

        async(() -> dao.deleteCategory(category.id()), deleted -> {
            changed = true;
            clearError();
            load();
        });
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================

    private <T> void async(Work<T> work, Consumer<T> onSuccess) {
        Thread worker = new Thread(() -> {
            T result = null;
            Throwable failure = null;
            try {
                result = work.run();
            } catch (Throwable t) {
                failure = t;
            }

            final T finalResult = result;
            final Throwable finalFailure = failure;
            Platform.runLater(() -> {
                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    showError("Something went wrong: " + finalFailure.getMessage());
                    load();   // show what the database really holds
                } else {
                    onSuccess.accept(finalResult);
                }
            });
        }, "category-manager");
        worker.setDaemon(true);
        worker.start();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}