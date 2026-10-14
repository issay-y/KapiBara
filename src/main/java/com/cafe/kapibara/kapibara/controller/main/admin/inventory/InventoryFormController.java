package com.cafe.kapibara.kapibara.controller.main.admin.inventory;

import com.cafe.kapibara.kapibara.DAO.InventoryDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.model.inventory.InventoryForm;
import com.cafe.kapibara.kapibara.model.inventory.StockStatus;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TreeSet;

/**
 * One form for both "Add inventory item" and "Edit inventory item".
 * Edit mode is used when the page was opened with an item id as its payload.
 * Categories come from the inventory_categories table and can be managed from here.
 */
public class InventoryFormController implements Initializable, AdminShell.Aware {

    private static final List<String> DEFAULT_UNITS =
            List.of("kg", "g", "liters", "ml", "pcs", "packs", "cans", "boxes", "bottles");
    private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    @FXML private Label titleLabel, subtitleLabel;
    @FXML private TextField nameField, quantityField, minimumField, statusField, notesField;
    @FXML private ComboBox<String> categoryCombo, unitCombo;
    @FXML private Label footNoteLabel, errorLabel;
    @FXML private Button cancelBtn, saveBtn;

    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private AdminShell shell;

    private boolean editMode;
    private long editingId;
    private String saveText = "✓   Save item";

    private boolean loading = true;   // true while fields are being filled in code
    private boolean dirty;            // true once the person has changed something

    private final ChangeListener<Object> markDirty = (obs, old, now) -> {
        if (!loading) dirty = true;
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        numbersOnly(quantityField);
        numbersOnly(minimumField);
        quantityField.setText("0");
        minimumField.setText("0");
        statusField.setEditable(false);
        statusField.setFocusTraversable(false);

        nameField.textProperty().addListener(markDirty);
        notesField.textProperty().addListener(markDirty);
        quantityField.textProperty().addListener(markDirty);
        minimumField.textProperty().addListener(markDirty);
        categoryCombo.valueProperty().addListener(markDirty);
        unitCombo.valueProperty().addListener(markDirty);

        // Status is worked out from quantity and minimum, so it updates as they type.
        quantityField.textProperty().addListener((obs, old, now) -> updateStatusPreview());
        minimumField.textProperty().addListener((obs, old, now) -> updateStatusPreview());
        updateStatusPreview();

        clearError();
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;

        Object payload = shell.getPagePayload();
        if (payload instanceof Long id) {
            editMode = true;
            editingId = id;
        }
        configureTexts();

        saveBtn.setDisable(true);   // until the lists (and the item, when editing) have loaded
        loadLookups();
    }

    // =====================================================================
    //  SETUP
    // =====================================================================

    private void configureTexts() {
        if (editMode) {
            titleLabel.setText("Edit inventory item");
            subtitleLabel.setText("Update stock details for this item.");
            footNoteLabel.setText("");
            saveText = "✓   Save changes";
        } else {
            titleLabel.setText("Add inventory item");
            subtitleLabel.setText("Add an ingredient or supply to your café's inventory.");
            footNoteLabel.setText("Inventory details can be edited later.");
            saveText = "✓   Save item";
        }
        saveBtn.setText(saveText);
    }

    /** Only digits and one decimal point (up to 2 decimals), so quantities can't be negative or text. */
    private static void numbersOnly(TextField field) {
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,7}([.]\\d{0,2})?") ? change : null));
    }

    private void loadLookups() {
        Thread worker = new Thread(() -> {
            List<String> categories = List.of();
            Optional<InventoryForm> found = Optional.empty();
            Throwable failure = null;
            try {
                categories = inventoryDAO.findCategories();
                if (editMode) found = inventoryDAO.findById(editingId);
            } catch (Throwable t) {
                failure = t;
            }

            final List<String> finalCategories = categories;
            final Optional<InventoryForm> finalFound = found;
            final Throwable finalFailure = failure;
            Platform.runLater(() -> {
                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    AlertsUtil.error(saveBtn, "Could not load",
                            "Please make sure the database is running and try again.");
                    shell.navigate(AdminPage.INVENTORY);
                    return;
                }
                if (editMode && finalFound.isEmpty()) {
                    AlertsUtil.warning(saveBtn, "Item not found", "This item no longer exists.");
                    shell.navigate(AdminPage.INVENTORY);
                    return;
                }
                fill(finalCategories, finalFound.orElse(null));
            });
        }, "load-inventory-form");
        worker.setDaemon(true);
        worker.start();
    }

    private void fill(List<String> categoriesInDb, InventoryForm form) {
        loading = true;

        // Categories come from the inventory_categories table only.
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categories.addAll(categoriesInDb);
        if (form != null) categories.add(form.category());
        categoryCombo.getItems().setAll(categories);

        LinkedHashSet<String> units = new LinkedHashSet<>(DEFAULT_UNITS);
        if (form != null) units.add(form.unit());
        unitCombo.getItems().setAll(units);

        if (form != null) {
            nameField.setText(form.name());
            categoryCombo.setValue(form.category());
            quantityField.setText(numberText(form.quantity()));
            unitCombo.setValue(form.unit());
            minimumField.setText(numberText(form.minimum()));
            notesField.setText(form.notes() == null ? "" : form.notes());
            subtitleLabel.setText("Update stock details for " + form.name() + ".");
            footNoteLabel.setText(lastUpdatedText(form));
        }

        updateStatusPreview();
        saveBtn.setDisable(false);
        loading = false;
        dirty = false;
    }

    private static String lastUpdatedText(InventoryForm form) {
        String date = form.updatedAt() == null ? "" : form.updatedAt().format(UPDATED);
        String by = form.updatedBy() == null || form.updatedBy().isBlank() ? "" : "by " + form.updatedBy();
        String text = ("Last updated " + by + " · " + date).replace("  ", " ").trim();
        return text.endsWith("·") ? text.substring(0, text.length() - 1).trim() : text;
    }

    private static String numberText(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }

    private void updateStatusPreview() {
        Double quantity = parse(quantityField.getText());
        Double minimum = parse(minimumField.getText());
        statusField.setText(quantity == null || minimum == null
                ? "—"
                : StockStatus.of(quantity, minimum).getDisplayName());
    }

    private static Double parse(String text) {
        if (text == null || text.isBlank() || ".".equals(text.trim())) return null;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // =====================================================================
    //  CATEGORIES (add / delete live in the Manage categories window)
    // =====================================================================

    @FXML
    private void handleManageCategories(ActionEvent event) {
        CategoryManagerDialog.show(saveBtn, this::reloadCategories);
    }

    /** Reloads the category list after the Manage categories window changed something. */
    private void reloadCategories() {
        Thread worker = new Thread(() -> {
            List<String> categories = List.of();
            Throwable failure = null;
            try {
                categories = inventoryDAO.findCategories();
            } catch (Throwable t) {
                failure = t;
            }

            final List<String> finalCategories = categories;
            final Throwable finalFailure = failure;
            Platform.runLater(() -> {
                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    AlertsUtil.error(saveBtn, "Could not load categories",
                            "Please make sure the database is running and try again.");
                    return;
                }
                String selected = categoryCombo.getValue();
                boolean wasLoading = loading;
                loading = true;   // reloading the list is not a change made by the person
                categoryCombo.getItems().setAll(finalCategories);
                categoryCombo.setValue(selected != null && finalCategories.contains(selected) ? selected : null);
                loading = wasLoading;
            });
        }, "reload-categories");
        worker.setDaemon(true);
        worker.start();
    }

    // =====================================================================
    //  ACTIONS (every one of them asks first)
    // =====================================================================

    @FXML
    private void handleCancel(ActionEvent event) {
        boolean leave = AlertsUtil.confirm(cancelBtn, AlertsUtil.Type.CONFIRMATION,
                editMode ? "Discard your changes?" : "Discard this new item?",
                editMode ? "Your changes to this item won't be saved."
                        : "This item won't be added and what you entered will be lost.",
                "Discard");
        if (leave) shell.navigate(AdminPage.INVENTORY);
    }

    /** "Back to inventory": asks first only if something was changed. */
    @FXML
    private void handleBack(ActionEvent event) {
        if (!dirty) {
            shell.navigate(AdminPage.INVENTORY);
            return;
        }
        boolean leave = AlertsUtil.confirm(cancelBtn, AlertsUtil.Type.CONFIRMATION, "Leave without saving?",
                "You have unsaved changes. They will be lost if you go back.",
                "Leave");
        if (leave) shell.navigate(AdminPage.INVENTORY);
    }

    @FXML
    private void handleSave(ActionEvent event) {
        String problem = validate();
        if (problem != null) {
            showError(problem);
            return;
        }
        clearError();

        final InventoryForm form = collectForm();

        boolean confirmed = AlertsUtil.confirm(saveBtn, AlertsUtil.Type.CONFIRMATION,
                editMode ? "Save your changes?" : "Save this item?",
                editMode ? "Update " + form.name() + " with these details?"
                        : "Add " + form.name() + " to the inventory?",
                "Save");
        if (!confirmed) return;

        final String user = shell.getCurrentUserName();

        saveBtn.setDisable(true);
        saveBtn.setText("Saving...");

        Thread worker = new Thread(() -> {
            String duplicate = null;
            Throwable failure = null;
            try {
                if (inventoryDAO.nameExists(form.name(), editMode ? editingId : 0)) {
                    duplicate = "An item with this name already exists.";
                } else if (editMode) {
                    inventoryDAO.update(form, user);
                } else {
                    inventoryDAO.insert(form, user);
                }
            } catch (Throwable t) {
                failure = t;
            }

            final String finalDuplicate = duplicate;
            final Throwable finalFailure = failure;
            Platform.runLater(() -> {
                saveBtn.setDisable(false);
                saveBtn.setText(saveText);

                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    AlertsUtil.error(saveBtn, "Could not save",
                            "Something went wrong while saving:\n" + finalFailure.getMessage());
                } else if (finalDuplicate != null) {
                    showError(finalDuplicate);
                    nameField.requestFocus();
                } else {
                    dirty = false;
                    AlertsUtil.info(saveBtn,
                            editMode ? "Changes saved" : "Item added",
                            editMode ? form.name() + " has been updated."
                                    : form.name() + " has been added to the inventory.");
                    shell.navigate(AdminPage.INVENTORY);
                }
            });
        }, "save-inventory-item");
        worker.setDaemon(true);
        worker.start();
    }

    // =====================================================================
    //  VALIDATION + HELPERS
    // =====================================================================

    /** @return a message describing the first problem, or null if the form is valid */
    private String validate() {
        String name = nameField.getText().trim();

        if (name.isEmpty()) return "Please enter the item name.";
        if (name.length() > 100) return "The item name can be at most 100 characters.";
        if (categoryCombo.getValue() == null) {
            return categoryCombo.getItems().isEmpty()
                    ? "There are no categories yet. Use \"Manage categories\" to add one."
                    : "Please choose a category.";
        }
        if (parse(quantityField.getText()) == null) return "Please enter the quantity (0 or more).";
        if (unitCombo.getValue() == null) return "Please choose a unit.";
        if (parse(minimumField.getText()) == null) return "Please enter the minimum stock (0 or more).";
        if (notesField.getText().trim().length() > 255) return "Notes can be at most 255 characters.";
        return null;
    }

    private InventoryForm collectForm() {
        String notes = notesField.getText().trim();
        return new InventoryForm(
                editMode ? editingId : 0,
                nameField.getText().trim(),
                categoryCombo.getValue(),
                parse(quantityField.getText()),
                unitCombo.getValue(),
                parse(minimumField.getText()),
                notes.isEmpty() ? null : notes,
                null,
                null);
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