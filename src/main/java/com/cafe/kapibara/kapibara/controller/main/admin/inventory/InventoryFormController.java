package com.cafe.kapibara.kapibara.controller.main.admin.inventory;

import com.cafe.kapibara.kapibara.DAO.InventoryDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.model.inventory.InventoryForm;
import com.cafe.kapibara.kapibara.model.inventory.StockStatus;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import com.cafe.kapibara.kapibara.util.InventoryImages;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * An item can carry one photo (PNG / JPG, up to 5 MB).
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

    // photo card
    @FXML private StackPane photoBox;
    @FXML private ImageView photoView;
    @FXML private VBox photoPlaceholder;
    @FXML private Button photoBtn, removePhotoBtn;

    private static final double PHOTO_SIZE = 200;   // must match the StackPane size in the FXML
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private AdminShell shell;

    private boolean editMode;
    private long editingId;
    private String saveText = "✓   Save item";

    private boolean loading = true;   // true while fields are being filled in code
    private boolean dirty;            // true once the person has changed something

    // photo state
    private String currentImage;      // file name already stored for this item (edit mode)
    private Path pickedFile;          // a new photo chosen in this session, not saved yet
    private boolean removeImage;      // the stored photo should be removed on save
    private Image photoImage;         // what the preview currently shows

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

        // Fixed-size circular photo. No listeners, so it can never feed back into layout.
        photoBox.setClip(new Circle(PHOTO_SIZE / 2, PHOTO_SIZE / 2, PHOTO_SIZE / 2));
        photoView.setFitWidth(PHOTO_SIZE);
        photoView.setFitHeight(PHOTO_SIZE);
        showPhoto(null);

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

            currentImage = form.imagePath();
            InventoryImages.find(currentImage)
                    .ifPresent(p -> showPhoto(new Image(p.toUri().toString(), 1200, 0, true, true)));
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
    //  PHOTO
    // =====================================================================

    @FXML
    private void handleChoosePhoto(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an item photo");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images (PNG, JPG)", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(photoBtn.getScene().getWindow());
        if (file == null) return;

        Path path = file.toPath();
        try {
            if (!InventoryImages.isSupported(path)) {
                showError("Please choose a PNG or JPG image.");
                return;
            }
            if (Files.size(path) > InventoryImages.MAX_BYTES) {
                showError("That photo is larger than 5 MB. Please choose a smaller one.");
                return;
            }
        } catch (IOException e) {
            showError("Could not read that file.");
            return;
        }

        Image image = new Image(path.toUri().toString(), 1200, 0, true, true);
        if (image.isError()) {
            showError("That image could not be opened. Try another file.");
            return;
        }

        clearError();
        pickedFile = path;
        removeImage = false;
        dirty = true;
        showPhoto(image);
    }

    @FXML
    private void handleRemovePhoto(ActionEvent event) {
        pickedFile = null;
        removeImage = currentImage != null;
        dirty = true;
        showPhoto(null);
    }

    private void showPhoto(Image image) {
        photoImage = image;
        photoView.setImage(image);
        photoView.setVisible(image != null);

        photoPlaceholder.setVisible(image == null);
        photoPlaceholder.setManaged(image == null);

        photoBox.getStyleClass().remove("adm-photo-box-filled");
        if (image != null) photoBox.getStyleClass().add("adm-photo-box-filled");

        photoBtn.setText(image == null ? "Choose photo" : "Change photo");
        removePhotoBtn.setDisable(image == null);
        layoutPhoto();
    }

    /** Crops the picture to a centered square so it fills the circle (like a cover photo). */
    private void layoutPhoto() {
        if (photoImage == null || photoImage.getWidth() <= 0) {
            photoView.setViewport(null);
            return;
        }
        double iw = photoImage.getWidth();
        double ih = photoImage.getHeight();
        double side = Math.min(iw, ih);
        photoView.setViewport(new Rectangle2D((iw - side) / 2, (ih - side) / 2, side, side));
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
        final Path picked = pickedFile;
        final boolean remove = removeImage;
        final String previous = currentImage;

        saveBtn.setDisable(true);
        saveBtn.setText("Saving...");

        Thread worker = new Thread(() -> {
            String duplicate = null;
            Throwable failure = null;
            String stored = previous;
            boolean replaced = false;
            try {
                if (inventoryDAO.nameExists(form.name(), editMode ? editingId : 0)) {
                    duplicate = "An item with this name already exists.";
                } else {
                    if (picked != null) {
                        stored = InventoryImages.save(picked);
                        replaced = true;
                    } else if (remove) {
                        stored = null;
                        replaced = true;
                    }
                    InventoryForm toSave = form.withImage(stored);
                    if (editMode) {
                        inventoryDAO.update(toSave, user);
                    } else {
                        inventoryDAO.insert(toSave, user);
                    }
                    if (replaced && previous != null) InventoryImages.delete(previous);
                }
            } catch (Throwable t) {
                failure = t;
                // the database save failed, so don't leave the copied photo behind
                if (replaced && stored != null && !stored.equals(previous)) InventoryImages.delete(stored);
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
                null,
                currentImage);
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