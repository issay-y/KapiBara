package com.cafe.kapibara.kapibara.controller.main.admin.inventory;

import com.cafe.kapibara.kapibara.DAO.InventoryDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.model.inventory.InventoryForm;
import com.cafe.kapibara.kapibara.model.inventory.InventoryRules;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TreeSet;

public class InventoryFormController implements Initializable, AdminShell.Aware {

    /** The form no longer asks for a unit; new items get this one. */
    private static final String DEFAULT_UNIT = "pcs";
    private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    @FXML private Label titleLabel, subtitleLabel, quantityLabel;
    @FXML private TextField nameField, quantityField, priceField, statusField, notesField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private Label footNoteLabel, errorLabel;
    @FXML private Button cancelBtn, saveBtn;

    // photo card
    @FXML private StackPane photoBox;
    @FXML private ImageView photoView;
    @FXML private VBox photoPlaceholder;
    @FXML private Button photoBtn, removePhotoBtn;

    private static final double PHOTO_SIZE = 200;
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private AdminShell shell;

    private boolean editMode;
    private long editingId;
    private String saveText = "✓   Save item";

    private boolean loading = true;
    private boolean dirty;

    // Hidden value: not shown on the form, but still sent to the DAO.
    // (The minimum stock is hard-coded in InventoryRules.)
    private String storedUnit = DEFAULT_UNIT;

    private String currentImage;
    private Path pickedFile;
    private boolean removeImage;
    private Image photoImage;

    private final ChangeListener<Object> markDirty = (obs, old, now) -> {
        if (!loading) dirty = true;
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        numbersOnly(quantityField);
        numbersOnly(priceField);
        quantityField.setText("0");
        priceField.setText("0");
        statusField.setEditable(false);
        statusField.setFocusTraversable(false);

        nameField.textProperty().addListener(markDirty);
        notesField.textProperty().addListener(markDirty);
        quantityField.textProperty().addListener(markDirty);
        priceField.textProperty().addListener(markDirty);
        categoryCombo.valueProperty().addListener(markDirty);

        quantityField.textProperty().addListener((obs, old, now) -> updateStatusPreview());
        updateStatusPreview();

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

        saveBtn.setDisable(true);
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
            quantityLabel.setText("Quantity *");
            saveText = "✓   Save changes";
        } else {
            titleLabel.setText("Add inventory item");
            subtitleLabel.setText("Add an ingredient or supply to your café's inventory.");
            footNoteLabel.setText("Inventory details can be edited later.");
            quantityLabel.setText("Initial quantity *");
            saveText = "✓   Save item";
        }
        saveBtn.setText(saveText);
    }

    private static void numbersOnly(TextField field) {
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,7}([.]\\d{0,2})?") ? change : null));
    }

    private void loadLookups() {
        Thread worker = new Thread(() -> {
            List<String> categories = List.of();
            Optional<InventoryForm> found = Optional.empty();
            BigDecimal price = BigDecimal.ZERO;
            Throwable failure = null;
            try {
                categories = inventoryDAO.findCategories();
                if (editMode) {
                    found = inventoryDAO.findById(editingId);
                    price = inventoryDAO.findPrice(editingId);
                }
            } catch (Throwable t) {
                failure = t;
            }

            final List<String> finalCategories = categories;
            final Optional<InventoryForm> finalFound = found;
            final BigDecimal finalPrice = price;
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
                fill(finalCategories, finalFound.orElse(null), finalPrice);
            });
        }, "load-inventory-form");
        worker.setDaemon(true);
        worker.start();
    }

    private void fill(List<String> categoriesInDb, InventoryForm form, BigDecimal price) {
        loading = true;

        // Categories come from the inventory_categories table only.
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categories.addAll(categoriesInDb);
        if (form != null) categories.add(form.category());
        categoryCombo.getItems().setAll(categories);

        if (form != null) {
            // keep the values the form no longer shows, so editing doesn't wipe them
            storedUnit = form.unit() == null || form.unit().isBlank() ? DEFAULT_UNIT : form.unit();

            nameField.setText(form.name());
            categoryCombo.setValue(form.category());
            quantityField.setText(numberText(form.quantity()));
            priceField.setText(numberText(price == null ? 0 : price.doubleValue()));
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
        statusField.setText(quantity == null
                ? "—"
                : StockStatus.of(quantity, InventoryRules.MINIMUM_STOCK).getDisplayName());
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
        final BigDecimal price = priceValue();

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
                        stored = InventoryImages.save(picked, form.category());
                        replaced = true;
                    } else if (remove) {
                        stored = null;
                        replaced = true;
                    }
                    InventoryForm toSave = form.withImage(stored);
                    if (editMode) {
                        inventoryDAO.update(toSave, price, user);
                    } else {
                        inventoryDAO.insert(toSave, price, user);
                    }
                    if (replaced && previous != null) InventoryImages.delete(previous);
                }
            } catch (Throwable t) {
                failure = t;
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
                    if (editMode) {
                        AlertsUtil.info(saveBtn, "Changes saved", form.name() + " has been updated.");
                        shell.navigate(AdminPage.INVENTORY);
                    } else {
                        boolean another = AlertsUtil.confirm(saveBtn, AlertsUtil.Type.CONFIRMATION,
                                "Item added",
                                form.name() + " has been added to the inventory.\nDo you want to add another item?",
                                "Add another");
                        if (another) {
                            resetForm();
                        } else {
                            shell.navigate(AdminPage.INVENTORY);
                        }
                    }
                }
            });
        }, "save-inventory-item");
        worker.setDaemon(true);
        worker.start();
    }

    // =====================================================================
    //  VALIDATION + HELPERS
    // =====================================================================

    private String validate() {
        String name = nameField.getText().trim();

        if (name.isEmpty()) return "Please enter the item name.";
        if (name.length() > 100) return "The item name can be at most 100 characters.";
        if (categoryCombo.getValue() == null) {
            return categoryCombo.getItems().isEmpty()
                    ? "There are no categories yet. Use \"Manage categories\" to add one."
                    : "Please choose a category.";
        }
        Double quantity = parse(quantityField.getText());
        if (quantity == null) return "Please enter the initial quantity (0 or more).";
        if (quantity > InventoryRules.MAXIMUM_QUANTITY) {
            return "The quantity can be at most " + InventoryRules.text(InventoryRules.MAXIMUM_QUANTITY) + ".";
        }
        if (parse(priceField.getText()) == null) return "Please enter the selling price (0 or more).";
        if (notesField.getText().trim().length() > 255) return "Notes can be at most 255 characters.";
        return null;
    }

    private BigDecimal priceValue() {
        String text = priceField.getText() == null ? "" : priceField.getText().trim();
        try {
            return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(2);
        }
    }

    private InventoryForm collectForm() {
        String notes = notesField.getText().trim();
        return new InventoryForm(
                editMode ? editingId : 0,
                nameField.getText().trim(),
                categoryCombo.getValue(),
                parse(quantityField.getText()),
                storedUnit,
                InventoryRules.MINIMUM_STOCK,
                notes.isEmpty() ? null : notes,
                null,
                null,
                currentImage);
    }

    private void resetForm() {
        loading = true;
        nameField.clear();
        notesField.clear();
        quantityField.setText("0");
        priceField.setText("0");
        categoryCombo.setValue(null);

        storedUnit = DEFAULT_UNIT;

        pickedFile = null;
        removeImage = false;
        currentImage = null;
        showPhoto(null);

        updateStatusPreview();
        clearError();

        loading = false;
        dirty = false;
        nameField.requestFocus();
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