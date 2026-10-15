package com.cafe.kapibara.kapibara.controller.main.admin.inventory;

import com.cafe.kapibara.kapibara.DAO.InventoryDAO;
import com.cafe.kapibara.kapibara.model.inventory.InventoryCategory;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * "Manage categories". Adds and deletes are staged in the window:
 * Cancel throws them away, Done saves them all and tells the caller to reload.
 * The window has no title bar and has rounded corners; Cancel and Done are the only ways out.
 * The list is as tall as its rows. A scrollbar only appears once it passes MAX_LIST_HEIGHT.
 */
public final class CategoryManagerDialog {

    private static final int MAX_NAME = 50;

    /** Tallest the list gets before it scrolls (7 rows fit; the 8th adds the scrollbar). */
    private static final double MAX_LIST_HEIGHT = 420;
    private static final double CORNER_RADIUS = 22;

    private interface Work<T> { T run() throws Exception; }

    /** One row in the list. id == 0 means "added here, not saved yet". */
    private record Entry(long id, String name, int itemCount) {
        boolean isNew() { return id == 0; }
    }

    public static void show(Node owner, Runnable onChanged) {
        new CategoryManagerDialog(owner, onChanged).stage.show();
    }

    private final InventoryDAO dao = new InventoryDAO();
    private final Node owner;
    private final Runnable onChanged;
    private final Stage stage = new Stage();

    private final List<Entry> original = new ArrayList<>();   // as in the database
    private final List<Entry> working = new ArrayList<>();    // what the list shows now

    private final TextField nameField = new TextField();
    private final Label errorLabel = new Label();
    private final Label summaryLabel = new Label("Loading…");
    private final VBox rows = new VBox();
    private final Button addBtn = new Button("＋   Add");
    private final Button cancelBtn = new Button("Cancel");
    private final Button doneBtn = new Button("✓   Done");

    private double dragX, dragY;
    private boolean loaded;       // the list has been read from the database at least once
    private boolean positioned;   // the window has been centred over its owner

    private CategoryManagerDialog(Node owner, Runnable onChanged) {
        this.owner = owner;
        this.onChanged = onChanged;
        buildUi();
        load();
    }

    // =====================================================================
    //  UI
    // =====================================================================

    private void buildUi() {
        Label title = new Label("Manage categories");
        title.getStyleClass().add("adm-page-title");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #3b2a1a;");
        Label note = new Label("Add a new category, or remove one that no item uses.");
        note.getStyleClass().add("adm-page-sub");
        note.setStyle("-fx-text-fill: #8a7662;");
        VBox header = new VBox(4, title, note);
        header.setCursor(javafx.scene.Cursor.MOVE);   // the header drags the window (there is no title bar)
        header.setOnMousePressed(e -> {
            dragX = e.getScreenX() - stage.getX();
            dragY = e.getScreenY() - stage.getY();
        });
        header.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragX);
            stage.setY(e.getScreenY() - dragY);
        });

        nameField.setPromptText("New category name, e.g. Syrups");
        nameField.getStyleClass().add("adm-field");
        HBox.setHgrow(nameField, Priority.ALWAYS);
        nameField.setOnAction(e -> handleAdd());

        addBtn.getStyleClass().add("adm-primary-btn");
        addBtn.setMinHeight(46);
        addBtn.setOnAction(e -> handleAdd());

        HBox addRow = new HBox(12, nameField, addBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);

        errorLabel.getStyleClass().add("adm-form-error");
        errorLabel.setStyle("-fx-text-fill: #b3412f;");
        errorLabel.setWrapText(true);
        clearError();

        summaryLabel.setStyle("-fx-text-fill: #8a7662; -fx-font-size: 13px;");

        // The scroll pane is exactly as tall as its rows (capped at MAX_LIST_HEIGHT),
        // so a short list has no scrollbar and a long one scrolls.
        rows.setFillWidth(true);
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
        scroll.prefHeightProperty().bind(Bindings.min(rows.heightProperty().add(2), MAX_LIST_HEIGHT));
        // the window grows and shrinks with the list
        scroll.prefHeightProperty().addListener((obs, old, now) -> Platform.runLater(this::fitWindow));

        VBox listCard = new VBox(scroll);
        listCard.setStyle("-fx-background-color: white; -fx-background-radius: 14; "
                + "-fx-border-color: #eadfce; -fx-border-radius: 14; -fx-padding: 6 10 6 10;");

        cancelBtn.getStyleClass().add("adm-page-btn");
        cancelBtn.setOnAction(e -> handleCancel());
        doneBtn.getStyleClass().add("adm-primary-btn");
        doneBtn.setOnAction(e -> handleDone());
        doneBtn.setDisable(true);   // until the list has loaded

        HBox footer = new HBox(14, cancelBtn, doneBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);

        // the rounded card
        VBox root = new VBox(16, header, addRow, errorLabel, summaryLabel, listCard, footer);
        root.getStyleClass().addAll("adm-root", "adm-lg");
        root.setStyle("-fx-background-color: #fbf7f1; -fx-background-radius: " + CORNER_RADIUS + "; "
                + "-fx-border-color: #d9c7ad; -fx-border-width: 1; -fx-border-radius: " + CORNER_RADIUS + ";");
        root.setPadding(new Insets(28, 32, 24, 32));
        root.setPrefWidth(580);
        root.setMinWidth(580);
        root.setMaxWidth(580);
        root.setEffect(new DropShadow(18, 0, 4, Color.rgb(60, 40, 20, 0.28)));

        // a transparent margin around the card, so the rounded corners (and the shadow) show
        StackPane shell = new StackPane(root);
        shell.setPadding(new Insets(20));
        shell.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(shell);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().addAll(collectStylesheets(owner));   // same look as the app
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) handleCancel();
        });

        stage.initStyle(StageStyle.TRANSPARENT);   // no title bar, and the corners can be rounded
        if (owner.getScene() != null) stage.initOwner(owner.getScene().getWindow());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Manage categories");
        stage.setScene(scene);
        stage.setOnShown(e -> nameField.requestFocus());
        // Alt+F4 behaves like Cancel
        stage.setOnCloseRequest(e -> {
            e.consume();
            handleCancel();
        });
    }

    /** The app's stylesheets may sit on the scene or on a parent node, so look in both places. */
    private static Set<String> collectStylesheets(Node owner) {
        Set<String> sheets = new LinkedHashSet<>();
        if (owner.getScene() != null) sheets.addAll(owner.getScene().getStylesheets());
        for (Node n = owner; n != null; n = n.getParent()) {
            if (n instanceof Parent p) sheets.addAll(p.getStylesheets());
        }
        return sheets;
    }

    /** Resizes the window to fit the list, keeping it centred where it is. */
    private void fitWindow() {
        if (!stage.isShowing()) return;

        double centerY = stage.getY() + stage.getHeight() / 2;
        stage.sizeToScene();

        if (loaded && !positioned) {
            positioned = true;
            centerOverOwner();
        } else if (positioned) {
            stage.setY(centerY - stage.getHeight() / 2);
        }
    }

    private void centerOverOwner() {
        Window w = owner.getScene() == null ? null : owner.getScene().getWindow();
        if (w == null) {
            stage.centerOnScreen();
            return;
        }
        stage.setX(w.getX() + (w.getWidth() - stage.getWidth()) / 2);
        stage.setY(w.getY() + (w.getHeight() - stage.getHeight()) / 2);
    }

    private void render() {
        rows.getChildren().clear();

        int items = working.stream().mapToInt(Entry::itemCount).sum();
        summaryLabel.setText(working.size() + (working.size() == 1 ? " category" : " categories")
                + "   ·   " + items + (items == 1 ? " item" : " items") + " in total"
                + (hasChanges() ? "   ·   unsaved changes" : ""));

        if (working.isEmpty()) {
            Label empty = new Label("No categories yet.\nAdd your first one above.");
            empty.setStyle("-fx-text-fill: #8a7662; -fx-padding: 30 0 30 0; -fx-text-alignment: center;");
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.setAlignment(Pos.CENTER);
            rows.getChildren().add(empty);
            return;
        }

        for (int i = 0; i < working.size(); i++) {
            rows.getChildren().add(row(working.get(i)));
            if (i < working.size() - 1) {
                Region line = new Region();
                line.setMinHeight(1);
                line.setPrefHeight(1);
                line.setMaxHeight(1);
                line.setStyle("-fx-background-color: #f0e6d8;");
                rows.getChildren().add(line);
            }
        }
    }

    private HBox row(Entry entry) {
        String letter = entry.name().isBlank() ? "?" : entry.name().substring(0, 1).toUpperCase(Locale.ROOT);
        Label letterLabel = new Label(letter);
        letterLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #6b4a2b;");
        StackPane badge = new StackPane(letterLabel);
        badge.setMinSize(36, 36);
        badge.setPrefSize(36, 36);
        badge.setMaxSize(36, 36);
        badge.setStyle("-fx-background-color: #f3e6d3; -fx-background-radius: 18;");

        Label name = new Label(entry.name());
        name.setStyle("-fx-font-size: 15px; -fx-text-fill: #3b2a1a;");

        Label count = new Label(entry.isNew() ? "New"
                : entry.itemCount() + (entry.itemCount() == 1 ? " item" : " items"));
        count.setStyle("-fx-background-color: #f3e6d3; -fx-background-radius: 10; "
                + "-fx-padding: 3 10 3 10; -fx-text-fill: #6b4a2b; -fx-font-size: 12px;");

        boolean inUse = entry.itemCount() > 0;
        Button delete = new Button("Delete");
        delete.setStyle("-fx-background-color: transparent; -fx-text-fill: #b3412f; -fx-cursor: hand;"
                + (inUse ? " -fx-opacity: 0.35;" : ""));
        if (inUse) {
            delete.setTooltip(new Tooltip("Move or delete its " + entry.itemCount()
                    + (entry.itemCount() == 1 ? " item" : " items") + " first."));
        }
        delete.setOnAction(e -> handleDelete(entry));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(14, badge, name, spacer, count, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 8, 10, 8));
        return row;
    }

    // =====================================================================
    //  LOAD / ADD / DELETE (staged; each one asks first)
    // =====================================================================

    private void load() {
        async(dao::findCategoryUsage, list -> {
            original.clear();
            working.clear();
            for (InventoryCategory c : list) {
                Entry e = new Entry(c.id(), c.name(), c.itemCount());
                original.add(e);
                working.add(e);
            }
            doneBtn.setDisable(false);
            loaded = true;
            render();
            Platform.runLater(() -> Platform.runLater(this::fitWindow));
        });
    }

    private void handleAdd() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            showError("Please enter a category name.");
            return;
        }
        if (name.length() > MAX_NAME) {
            showError("The category name can be at most " + MAX_NAME + " characters.");
            return;
        }
        for (Entry e : working) {
            if (e.name().equalsIgnoreCase(name)) {
                showError("A category named \"" + e.name() + "\" already exists.");
                return;
            }
        }
        clearError();

        boolean confirmed = AlertsUtil.confirm(addBtn, AlertsUtil.Type.CONFIRMATION,
                "Add this category?", "Add " + name + " to the categories?", "Add");
        if (!confirmed) return;

        working.add(new Entry(0, name, 0));
        working.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        nameField.clear();
        nameField.requestFocus();
        render();
    }

    private void handleDelete(Entry entry) {
        Node window = stage.getScene().getRoot();

        if (entry.itemCount() > 0) {
            AlertsUtil.warning(window, "Category in use",
                    entry.name() + " is used by " + entry.itemCount()
                            + (entry.itemCount() == 1 ? " item" : " items")
                            + ".\nMove or delete those items first.");
            return;
        }

        boolean confirmed = AlertsUtil.confirm(window, AlertsUtil.Type.WARNING, "Delete this category?",
                "Delete " + entry.name() + "?\nIt is removed for good when you press Done.", "Delete");
        if (!confirmed) return;

        working.remove(entry);
        clearError();
        render();
    }

    // =====================================================================
    //  CANCEL / DONE
    // =====================================================================

    private void handleCancel() {
        boolean discard = AlertsUtil.confirm(cancelBtn, AlertsUtil.Type.CONFIRMATION,
                hasChanges() ? "Discard your changes?" : "Close without changes?",
                hasChanges() ? "The categories you added or removed won't be saved."
                        : "No changes were made to the categories.",
                hasChanges() ? "Discard" : "Close");
        if (discard) stage.close();
    }

    private void handleDone() {
        if (!hasChanges()) {
            stage.close();
            return;
        }

        boolean confirmed = AlertsUtil.confirm(doneBtn, AlertsUtil.Type.CONFIRMATION,
                "Save your changes?", "Apply the category changes you made?", "Save");
        if (!confirmed) return;

        List<Entry> toDelete = new ArrayList<>();
        for (Entry e : original) {
            if (!working.contains(e)) toDelete.add(e);
        }
        List<Entry> toAdd = new ArrayList<>();
        for (Entry e : working) {
            if (e.isNew()) toAdd.add(e);
        }

        doneBtn.setDisable(true);
        cancelBtn.setDisable(true);
        doneBtn.setText("Saving...");

        async(() -> {
            for (Entry e : toDelete) dao.deleteCategory(e.id());
            for (Entry e : toAdd) {
                if (!dao.categoryNameExists(e.name())) dao.insertCategory(e.name());
            }
            return Boolean.TRUE;
        }, ok -> {
            stage.close();
            if (onChanged != null) onChanged.run();
        });
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================

    private boolean hasChanges() {
        return !original.equals(working);
    }

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
                    cancelBtn.setDisable(false);
                    doneBtn.setText("✓   Done");
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