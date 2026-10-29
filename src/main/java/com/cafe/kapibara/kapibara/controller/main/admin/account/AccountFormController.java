package com.cafe.kapibara.kapibara.controller.main.admin.account;

import com.cafe.kapibara.kapibara.DAO.AccountDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.model.account.AccountForm;
import com.cafe.kapibara.kapibara.model.account.AccountRow;
import com.cafe.kapibara.kapibara.model.account.AccountRole;
import com.cafe.kapibara.kapibara.model.account.AccountStatus;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;


public class AccountFormController implements Initializable, AdminShell.Aware {

    private static final String USERNAME_PATTERN = "[A-Za-z0-9._-]{3,30}";

    // ---- password rules (hard-coded) -------------------------------------
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 10;
    private static final String ALLOWED_SYMBOLS = ".,_-";

    @FXML private Label titleLabel, subtitleLabel;
    @FXML private TextField fullNameField, usernameField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private ComboBox<String> statusCombo;   // no longer used; hidden if the FXML still has it
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

    // The status is no longer on the form: new accounts are Active, edited accounts keep theirs.
    private AccountStatus storedStatus = AccountStatus.ACTIVE;

    // Who is using the form, and (when editing) the role the account had when it was opened.
    // Admin: can add Cashier/Manager and change roles (never the admin account's).
    // Manager: can only add Cashier accounts and can't change any role.
    private AccountRole viewerRole;   // null until looked up; treated like a manager until then
    private AccountRole storedRole;

    private boolean loading = true;   // true while fields are being filled in code
    private boolean dirty;            // true once the person has changed something

    private final ChangeListener<Object> markDirty = (obs, old, now) -> {
        if (!loading) dirty = true;
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // The Admin role can't be picked: the admin account is permanent.
        for (AccountRole role : AccountRole.values()) {
            if (role != AccountRole.ADMIN) roleCombo.getItems().add(role.getDisplayName());
        }
        roleCombo.setValue(AccountRole.CASHIER.getDisplayName());

        hideStatus();
        mountPasswordRules();

        setupPasswordToggle(passwordField, passwordVisibleField, togglePasswordBtn);
        setupPasswordToggle(confirmField, confirmVisibleField, toggleConfirmBtn);

        fullNameField.textProperty().addListener(markDirty);
        usernameField.textProperty().addListener(markDirty);
        passwordField.textProperty().addListener(markDirty);
        confirmField.textProperty().addListener(markDirty);
        roleCombo.valueProperty().addListener(markDirty);

        clearError();
        setVisibleAndManaged(lockNoteLabel, false);
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;
        loadViewerRole();

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
        // No " *" in the labels: the form already draws its own red star next to them.
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
            passwordLabel.setText("Password");
            confirmLabel.setText("Confirm password");
            setPrompt(passwordField, passwordVisibleField, "Enter a password");
            setPrompt(confirmField, confirmVisibleField, "Re-enter password");
            saveText = "✓   Save account";
        }
        saveBtn.setText(saveText);
    }

    private static void setPrompt(PasswordField hidden, TextField visible, String text) {
        hidden.setPromptText(text);
        visible.setPromptText(text);
    }

    /** Removes the "Account status" field from the screen (its label goes with it). */
    private void hideStatus() {
        if (statusCombo == null) return;   // already removed from the FXML
        Node holder = statusCombo.getParent() instanceof VBox ? statusCombo.getParent() : statusCombo;
        holder.setVisible(false);
        holder.setManaged(false);
    }

    /** Replaces the old one-line hint with the "Password Requirements & Security Rules" box. */
    private void mountPasswordRules() {
        VBox card = buildRulesCard();

        Node parent = hintLabel.getParent();
        if (parent instanceof Pane box && !(parent instanceof GridPane)) {
            int index = box.getChildren().indexOf(hintLabel);
            if (index >= 0) {
                box.getChildren().set(index, card);
                return;
            }
        }
        // fallback if the layout is unusual: show the rules as plain text
        hintLabel.setWrapText(true);
        hintLabel.setText("Password rules: 8 to 10 characters; at least 1 uppercase letter, 1 digit and 1 symbol "
                + "(. , _ -); can't match the username; no repeating or sequential patterns (123, aaa).");
    }

    private static VBox buildRulesCard() {
        Label shield = new Label("🛡");
        shield.setStyle("-fx-font-size: 16px; -fx-text-fill: #4E2216;");
        Label title = new Label("Password Requirements & Security Rules");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4E2216;");
        HBox header = new HBox(10, shield, title);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox left = ruleColumn(
                "Length: strictly " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH + " characters",
                "At least 1 uppercase (A–Z)",
                "At least 1 numeric digit (0–9)");
        VBox right = ruleColumn(
                "At least 1 allowed symbol: . , _ or -",
                "Cannot match the username",
                "No repeating or sequential patterns (123, aaa)");

        HBox columns = new HBox(48, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        VBox card = new VBox(12, header, columns);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: #F6EEE3; -fx-background-radius: 12; "
                + "-fx-border-color: #E3D2BF; -fx-border-radius: 12; -fx-padding: 16 20 16 20;");
        return card;
    }

    private static VBox ruleColumn(String... rules) {
        VBox column = new VBox(8);
        column.setMaxWidth(Double.MAX_VALUE);
        for (String rule : rules) {
            Label line = new Label("•   " + rule);
            line.setWrapText(true);
            line.setStyle("-fx-font-size: 13px; -fx-text-fill: #8A7A70;");
            column.getChildren().add(line);
        }
        return column;
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

        storedStatus = form.status();   // keep the account's current status untouched
        storedRole = form.role();       // remembered so a locked role can't be changed

        fullNameField.setText(form.fullName());
        usernameField.setText(form.username());
        roleCombo.setValue(form.role().getDisplayName());
        subtitleLabel.setText("Update " + form.fullName() + "'s account details and workspace role.");

        applyRoleRules();

        saveBtn.setDisable(false);
        loading = false;
        dirty = false;
    }

    // =====================================================================
    //  ROLE RULES
    // =====================================================================

    /** Finds the signed-in person's role (matched by username or full name, like the Accounts page does). */
    private void loadViewerRole() {
        final String current = shell.getCurrentUserName() == null ? "" : shell.getCurrentUserName().trim();

        Thread worker = new Thread(() -> {
            AccountRole found = null;
            try {
                for (AccountRow row : accountDAO.findAll()) {
                    boolean mine = !current.isEmpty()
                            && (current.equalsIgnoreCase(row.username() == null ? "" : row.username())
                            || current.equalsIgnoreCase(row.fullName() == null ? "" : row.fullName()));
                    if (mine) {
                        found = row.role();
                        break;
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

            final AccountRole finalFound = found;
            Platform.runLater(() -> {
                viewerRole = finalFound;
                applyRoleRules();
            });
        }, "load-viewer-role");
        worker.setDaemon(true);
        worker.start();
    }

    /** True when the Role dropdown must not be changed by the person using the form. */
    private boolean isRoleLocked() {
        boolean viewerIsAdmin = viewerRole == AccountRole.ADMIN;
        boolean editingAdmin = editMode && storedRole == AccountRole.ADMIN;
        return editingAdmin || !viewerIsAdmin;
    }

    /** The role that is saved: the dropdown's choice, or the fixed one when the dropdown is locked. */
    private AccountRole finalRole() {
        if (!isRoleLocked()) return roleByName(roleCombo.getValue());
        return editMode && storedRole != null ? storedRole : AccountRole.CASHIER;
    }

    private void applyRoleRules() {
        boolean viewerIsAdmin = viewerRole == AccountRole.ADMIN;
        boolean editingAdmin = editMode && storedRole == AccountRole.ADMIN;
        boolean locked = isRoleLocked();

        boolean wasLoading = loading;
        loading = true;   // these are not changes made by the person
        try {
            if (!editMode) {
                String cashier = AccountRole.CASHIER.getDisplayName();
                List<String> options = new ArrayList<>();
                for (AccountRole role : AccountRole.values()) {
                    if (role == AccountRole.ADMIN) continue;
                    if (!viewerIsAdmin && role != AccountRole.CASHIER) continue;   // managers: cashier only
                    options.add(role.getDisplayName());
                }
                String current = roleCombo.getValue();
                roleCombo.getItems().setAll(options);
                roleCombo.setValue(current != null && options.contains(current) ? current : cashier);
            }
            roleCombo.setDisable(locked);
        } finally {
            loading = wasLoading;
        }

        if (editingAdmin) {
            lockNoteLabel.setText("This is the admin account. Its role is permanent.");
        } else if (locked) {
            lockNoteLabel.setText(editMode
                    ? "Only an admin can change roles."
                    : "Managers can only add cashier accounts.");
        }
        setVisibleAndManaged(lockNoteLabel, locked);
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

        boolean passwordRequired = !editMode || !password.isEmpty() || !confirm.isEmpty();
        if (passwordRequired) {
            String problem = passwordProblem(password, username);
            if (problem != null) return problem;
            if (!password.equals(confirm)) return "The passwords don't match.";
        }
        return null;
    }

    /** Checks the hard-coded password rules. @return the first broken rule, or null if the password is fine */
    private static String passwordProblem(String password, String username) {
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            return "Password must be " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH + " characters long.";
        }
        for (char c : password.toCharArray()) {
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || ALLOWED_SYMBOLS.indexOf(c) >= 0;
            if (!ok) return "Password can only use letters, numbers and these symbols: . , _ -";
        }
        if (!password.matches(".*[A-Z].*")) return "Password needs at least 1 uppercase letter (A–Z).";
        if (!password.matches(".*[0-9].*")) return "Password needs at least 1 number (0–9).";
        if (!containsAny(password, ALLOWED_SYMBOLS)) {
            return "Password needs at least 1 symbol: . , _ or -";
        }
        if (password.equalsIgnoreCase(username)) return "Password can't be the same as the username.";
        if (hasRepeatOrSequence(password)) {
            return "Password can't have repeating or sequential patterns (like 123 or aaa).";
        }
        return null;
    }

    private static boolean containsAny(String text, String chars) {
        for (char c : chars.toCharArray()) {
            if (text.indexOf(c) >= 0) return true;
        }
        return false;
    }

    /** True for 3 of the same character in a row (aaa) or 3 letters/digits in a row up or down (abc, 123, 321). */
    private static boolean hasRepeatOrSequence(String password) {
        String p = password.toLowerCase();
        for (int i = 0; i + 2 < p.length(); i++) {
            char a = p.charAt(i), b = p.charAt(i + 1), c = p.charAt(i + 2);
            if (a == b && b == c) return true;
            if (!Character.isLetterOrDigit(a) || !Character.isLetterOrDigit(b) || !Character.isLetterOrDigit(c)) {
                continue;
            }
            boolean up = b - a == 1 && c - b == 1;
            boolean down = a - b == 1 && b - c == 1;
            if (up || down) return true;
        }
        return false;
    }

    private AccountForm collectForm() {
        return new AccountForm(
                editMode ? editingId : 0,
                fullNameField.getText().trim(),
                usernameField.getText().trim(),
                finalRole(),
                storedStatus);
    }

    private static AccountRole roleByName(String displayName) {
        for (AccountRole role : AccountRole.values()) {
            if (role.getDisplayName().equals(displayName)) return role;
        }
        return AccountRole.CASHIER;
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