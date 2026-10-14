package com.cafe.kapibara.kapibara.controller.main.admin.account;

import com.cafe.kapibara.kapibara.DAO.AccountDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.model.account.AccountForm;
import com.cafe.kapibara.kapibara.model.account.AccountRole;
import com.cafe.kapibara.kapibara.model.account.AccountStatus;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;


public class AccountFormController implements Initializable, AdminShell.Aware {

    private static final String USERNAME_PATTERN = "[A-Za-z0-9._-]{3,30}";
    private static final int MIN_PASSWORD_LENGTH = 8;

    @FXML private Label titleLabel, subtitleLabel;
    @FXML private TextField fullNameField, usernameField;
    @FXML private ComboBox<String> roleCombo, statusCombo;
    @FXML private Label lockNoteLabel;

    @FXML private Label passwordSectionLabel, passwordLabel, confirmLabel, hintLabel, errorLabel;
    @FXML private PasswordField passwordField, confirmField;
    @FXML private TextField passwordVisibleField, confirmVisibleField;
    @FXML private Button togglePasswordBtn, toggleConfirmBtn;

    @FXML private Button cancelBtn, saveBtn;

    private final AccountDAO accountDAO = new AccountDAO();
    private AdminShell shell;

    private boolean editMode;
    private long editingId;
    private String saveText = "✓   Save account";

    private boolean loading = true;   // true while fields are being filled in code
    private boolean dirty;            // true once the person has changed something

    private final ChangeListener<Object> markDirty = (obs, old, now) -> {
        if (!loading) dirty = true;
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        for (AccountRole role : AccountRole.values()) {
            roleCombo.getItems().add(role.getDisplayName());
        }
        for (AccountStatus status : AccountStatus.values()) {
            statusCombo.getItems().add(status.getDisplayName());
        }
        roleCombo.setValue(AccountRole.CASHIER.getDisplayName());
        statusCombo.setValue(AccountStatus.ACTIVE.getDisplayName());

        setupPasswordToggle(passwordField, passwordVisibleField, togglePasswordBtn);
        setupPasswordToggle(confirmField, confirmVisibleField, toggleConfirmBtn);

        fullNameField.textProperty().addListener(markDirty);
        usernameField.textProperty().addListener(markDirty);
        passwordField.textProperty().addListener(markDirty);
        confirmField.textProperty().addListener(markDirty);
        roleCombo.valueProperty().addListener(markDirty);
        statusCombo.valueProperty().addListener(markDirty);

        clearError();
        setVisibleAndManaged(lockNoteLabel, false);
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;

        Object payload = shell.getPagePayload();
        if (payload instanceof Long id) {
            editMode = true;
            editingId = id;
            configureTexts();
            saveBtn.setDisable(true);   // until the account has loaded
            loadAccount();
        } else {
            configureTexts();
            loading = false;
        }
    }

    // =====================================================================
    //  SETUP
    // =====================================================================

    private void configureTexts() {
        if (editMode) {
            titleLabel.setText("Edit account");
            subtitleLabel.setText("Update this account's details and workspace role.");
            passwordSectionLabel.setText("Change password (optional)");
            passwordLabel.setText("New password");
            confirmLabel.setText("Confirm new password");
            setPrompt(passwordField, passwordVisibleField, "Leave blank to keep unchanged");
            setPrompt(confirmField, confirmVisibleField, "Re-enter password");
            saveText = "✓   Save changes";
        } else {
            titleLabel.setText("Add team account");
            subtitleLabel.setText("Set up a clear identity and role for your team member.");
            passwordSectionLabel.setText("Account password");
            passwordLabel.setText("Password *");
            confirmLabel.setText("Confirm password *");
            setPrompt(passwordField, passwordVisibleField, "Enter a password");
            setPrompt(confirmField, confirmVisibleField, "Re-enter password");
            saveText = "✓   Save account";
        }
        saveBtn.setText(saveText);
        hintLabel.setText("Use at least " + MIN_PASSWORD_LENGTH + " characters. Confirm the details with your team member.");
    }

    private static void setPrompt(PasswordField hidden, TextField visible, String text) {
        hidden.setPromptText(text);
        visible.setPromptText(text);
    }

    /** Eye button: shows or hides the password. Both fields always hold the same text. */
    private static void setupPasswordToggle(PasswordField hidden, TextField visible, Button button) {
        visible.textProperty().bindBidirectional(hidden.textProperty());
        button.setOnAction(e -> {
            boolean show = !visible.isVisible();
            visible.setVisible(show);
            visible.setManaged(show);
            hidden.setVisible(!show);
            hidden.setManaged(!show);
            button.setText(show ? "🙈" : "👁");

            TextField active = show ? visible : hidden;
            active.requestFocus();
            active.positionCaret(active.getText().length());
        });
    }

    private void loadAccount() {
        Thread worker = new Thread(() -> {
            Optional<AccountForm> found = Optional.empty();
            Throwable failure = null;
            try {
                found = accountDAO.findById(editingId);
            } catch (Throwable t) {
                failure = t;
            }

            final Optional<AccountForm> finalFound = found;
            final Throwable finalFailure = failure;
            Platform.runLater(() -> {
                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    AlertsUtil.error(saveBtn, "Could not load account",
                            "Please make sure the database is running and try again.");
                    shell.navigate(AdminPage.ACCOUNTS);
                    return;
                }
                if (finalFound.isEmpty()) {
                    AlertsUtil.warning(saveBtn, "Account not found", "This account no longer exists.");
                    shell.navigate(AdminPage.ACCOUNTS);
                    return;
                }
                populate(finalFound.get());
            });
        }, "load-account-form");
        worker.setDaemon(true);
        worker.start();
    }

    private void populate(AccountForm form) {
        loading = true;

        fullNameField.setText(form.fullName());
        usernameField.setText(form.username());
        roleCombo.setValue(form.role().getDisplayName());
        statusCombo.setValue(form.status().getDisplayName());
        subtitleLabel.setText("Update " + form.fullName() + "'s account details and workspace role.");

        // The admin account is permanent: its role and status can't be changed.
        if (form.role() == AccountRole.ADMIN) {
            roleCombo.setDisable(true);
            statusCombo.setDisable(true);
            lockNoteLabel.setText("This is the admin account. Its role and status are permanent.");
            setVisibleAndManaged(lockNoteLabel, true);
        }

        saveBtn.setDisable(false);
        loading = false;
        dirty = false;
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================

    @FXML
    private void handleCancel(ActionEvent event) {
        boolean leave = AlertsUtil.confirm(cancelBtn, AlertsUtil.Type.CONFIRMATION,
                editMode ? "Discard your changes?" : "Discard this new account?",
                editMode ? "Your changes to this account won't be saved."
                        : "This account won't be created and what you entered will be lost.",
                "Discard");
        if (leave) shell.navigate(AdminPage.ACCOUNTS);
    }

    /** "Back to accounts": asks first only if something was changed. */
    @FXML
    private void handleBack(ActionEvent event) {
        if (!dirty) {
            shell.navigate(AdminPage.ACCOUNTS);
            return;
        }
        boolean leave = AlertsUtil.confirm(cancelBtn, AlertsUtil.Type.CONFIRMATION, "Leave without saving?",
                "You have unsaved changes. They will be lost if you go back.",
                "Leave");
        if (leave) shell.navigate(AdminPage.ACCOUNTS);
    }

    @FXML
    private void handleSave(ActionEvent event) {
        String problem = validate();
        if (problem != null) {
            showError(problem);
            return;
        }
        clearError();

        final AccountForm form = collectForm();
        final String password = passwordField.getText();

        saveBtn.setDisable(true);
        saveBtn.setText("Saving...");

        Thread worker = new Thread(() -> {
            String taken = null;
            Throwable failure = null;
            try {
                if (accountDAO.usernameExists(form.username(), editMode ? editingId : 0)) {
                    taken = "That username is already taken. Please choose another.";
                } else if (editMode) {
                    accountDAO.update(form, password.isEmpty() ? null : password);
                } else {
                    accountDAO.insert(form, password);
                }
            } catch (Throwable t) {
                failure = t;
            }

            final String finalTaken = taken;
            final Throwable finalFailure = failure;
            Platform.runLater(() -> {
                saveBtn.setDisable(false);
                saveBtn.setText(saveText);

                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    AlertsUtil.error(saveBtn, "Could not save",
                            "Something went wrong while saving:\n" + finalFailure.getMessage());
                } else if (finalTaken != null) {
                    showError(finalTaken);
                    usernameField.requestFocus();
                } else {
                    dirty = false;
                    AlertsUtil.info(saveBtn,
                            editMode ? "Changes saved" : "Account created",
                            editMode ? form.fullName() + "'s account has been updated."
                                    : form.fullName() + " can now sign in as " + form.username() + ".");
                    shell.navigate(AdminPage.ACCOUNTS);
                }
            });
        }, "save-account");
        worker.setDaemon(true);
        worker.start();
    }

    // =====================================================================
    //  VALIDATION + HELPERS
    // =====================================================================

    /** @return a message describing the first problem, or null if the form is valid */
    private String validate() {
        String name = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmField.getText();

        if (name.isEmpty()) return "Please enter the full name.";
        if (username.isEmpty()) return "Please enter a username.";
        if (!username.matches(USERNAME_PATTERN)) {
            return "Username must be 3–30 characters: letters, numbers, dots, dashes or underscores.";
        }
        if (roleCombo.getValue() == null) return "Please choose a role.";
        if (statusCombo.getValue() == null) return "Please choose an account status.";

        boolean passwordRequired = !editMode || !password.isEmpty() || !confirm.isEmpty();
        if (passwordRequired) {
            if (password.length() < MIN_PASSWORD_LENGTH) {
                return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
            }
            if (!password.equals(confirm)) return "The passwords don't match.";
        }
        return null;
    }

    private AccountForm collectForm() {
        return new AccountForm(
                editMode ? editingId : 0,
                fullNameField.getText().trim(),
                usernameField.getText().trim(),
                roleByName(roleCombo.getValue()),
                statusByName(statusCombo.getValue()));
    }

    private static AccountRole roleByName(String displayName) {
        for (AccountRole role : AccountRole.values()) {
            if (role.getDisplayName().equals(displayName)) return role;
        }
        return AccountRole.CASHIER;
    }

    private static AccountStatus statusByName(String displayName) {
        for (AccountStatus status : AccountStatus.values()) {
            if (status.getDisplayName().equals(displayName)) return status;
        }
        return AccountStatus.ACTIVE;
    }

    private void showError(String message) {
        errorLabel.setText(message);
        setVisibleAndManaged(errorLabel, true);
    }

    private void clearError() {
        errorLabel.setText("");
        setVisibleAndManaged(errorLabel, false);
    }

    private static void setVisibleAndManaged(Label label, boolean show) {
        label.setVisible(show);
        label.setManaged(show);
    }
}