package com.cafe.kapibara.kapibara.controller.main.profile;

import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierShell;
import com.cafe.kapibara.kapibara.util.MessageBox;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.regex.Pattern;

/**
 * "My profile" for every role. The same page is loaded by the admin/manager shell and by the
 * cashier shell; each shell passes itself in through the matching attach(...) method.
 */
public class ProfileController implements AdminShell.Aware, CashierShell.Aware {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,}$");

    /** What this page edits (fullName, email, username) plus read-only facts shown on the left. */
    private record ProfileData(String fullName, String email, String username,
                               String branch, String joined) { }

    @FXML private Label initialsLabel;
    @FXML private Label nameLabel;
    @FXML private Label roleLabel;
    @FXML private Label statusLabel;
    @FXML private Label usernameValue;
    @FXML private Label branchValue;
    @FXML private Label joinedValue;
    @FXML private Label roleNoteLabel;
    @FXML private Label errorLabel;
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private TextField usernameField;
    @FXML private Button cancelBtn;
    @FXML private Button saveBtn;

    private String userName;
    private Runnable logoutAction;
    private ProfileData saved;

    @FXML
    private void initialize() {
        // Clear the error as soon as the person starts fixing the field.
        fullNameField.textProperty().addListener((obs, o, n) -> errorLabel.setText(""));
        emailField.textProperty().addListener((obs, o, n) -> errorLabel.setText(""));
        usernameField.textProperty().addListener((obs, o, n) -> errorLabel.setText(""));
    }

    @Override
    public void attach(AdminShell shell) {
        start(shell.getCurrentUserName(), shell.getCurrentUserRole(), shell::logout);
    }

    @Override
    public void attach(CashierShell shell) {
        start(shell.getCurrentUserName(), shell.getCurrentUserRole(), shell::logout);
    }

    private void start(String name, String role, Runnable logout) {
        this.userName = name;
        this.logoutAction = logout;

        roleLabel.setText(role);
        roleNoteLabel.setText("Admin".equalsIgnoreCase(role)
                ? "Roles are managed from Team accounts."
                : "Role changes are managed by your branch administrator.");

        saved = fetchProfile();
        showSaved();
    }

    // ------------------------------------------------------------------
    // Data layer: connect these two methods to your account service.
    // ------------------------------------------------------------------

    private ProfileData fetchProfile() {
        // TODO: load the signed-in user's row (full name, email, username, branch, joined date).
        return new ProfileData(userName, "", userName, "—", "—");
    }

    private void persist(ProfileData data) {
        // TODO: save through your account service.
        // Throw IllegalStateException("That username is already taken.") when the
        // username belongs to another account; the message is shown under the form.
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    @FXML
    private void handleSave() {
        errorLabel.setText("");
        ProfileData edited = readForm();

        String problem = validate(edited);
        if (problem != null) {
            errorLabel.setText(problem);
            return;
        }

        if (edited.equals(saved)) {
            MessageBox.info(saveBtn, "Nothing to save", "You haven't changed anything in your profile.");
            return;
        }

        if (!MessageBox.confirm(saveBtn, "Save profile?",
                "Your changes will replace the details saved on your account.",
                "Save profile", "Keep editing", false)) {
            return;
        }

        try {
            persist(edited);
        } catch (IllegalStateException ex) {
            errorLabel.setText(ex.getMessage());
            return;
        }

        saved = edited;
        showSaved();
        MessageBox.info(saveBtn, "Profile updated", "Your details were saved.");
    }

    @FXML
    private void handleCancel() {
        if (readForm().equals(saved)) {
            MessageBox.info(cancelBtn, "No changes to discard",
                    "Your profile already matches your saved details.");
            return;
        }
        if (MessageBox.confirm(cancelBtn, "Discard changes?",
                "Your edits will be lost and your saved details will come back.",
                "Discard changes", "Keep editing", true)) {
            errorLabel.setText("");
            showSaved();
        }
    }

    @FXML
    private void handleLogout() {
        logoutAction.run();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ProfileData readForm() {
        return new ProfileData(fullNameField.getText().trim(), emailField.getText().trim(),
                usernameField.getText().trim(), saved.branch(), saved.joined());
    }

    private String validate(ProfileData d) {
        if (d.fullName().isEmpty()) return "Enter your full name.";
        if (!EMAIL.matcher(d.email()).matches()) return "Enter a valid email address.";
        if (!USERNAME.matcher(d.username()).matches()) {
            return "Username needs at least 3 characters: letters, numbers, dot, dash or underscore.";
        }
        return null;
    }

    /** Puts the saved values into the form and the identity card. */
    private void showSaved() {
        fullNameField.setText(saved.fullName());
        emailField.setText(saved.email());
        usernameField.setText(saved.username());

        nameLabel.setText(saved.fullName());
        initialsLabel.setText(initialsOf(saved.fullName()));
        usernameValue.setText(saved.username());
        branchValue.setText(saved.branch());
        joinedValue.setText(saved.joined());
    }

    private static String initialsOf(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "–";
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }
}