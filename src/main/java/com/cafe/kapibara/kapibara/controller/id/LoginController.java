package com.cafe.kapibara.kapibara.controller.id;

import com.cafe.kapibara.kapibara.DAO.UserDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminController;
import com.cafe.kapibara.kapibara.model.account.AccountRole;
import com.cafe.kapibara.kapibara.model.account.User;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import com.cafe.kapibara.kapibara.util.WindowUtil;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

public class LoginController implements Initializable {

    private static final String PREF_USER = "remembered_username";
    private static final String PREF_REMEMBER = "remember_me";
    private static final String LOGIN_TEXT = "→  Log in";

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private Button togglePasswordBtn;
    @FXML private CheckBox rememberCheck;
    @FXML private Button loginBtn;

    @FXML private StackPane heroImageHolder;

    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
    private final UserDAO userDAO = new UserDAO();
    private boolean passwordShown = false;

    /** Outcome of a login attempt, computed off the FX thread. */
    private enum AuthStatus { SUCCESS, INVALID, INACTIVE }

    private record AuthResult(AuthStatus status, User user) {}

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());

        setupRoundedHeroImage(16);
        loadRememberedUser();

        usernameField.setOnAction(e -> activePasswordField().requestFocus());
        passwordField.setOnAction(this::handleLogin);
        passwordVisibleField.setOnAction(this::handleLogin);

        Platform.runLater(() -> {
            if (usernameField.getText().isEmpty()) usernameField.requestFocus();
            else activePasswordField().requestFocus();
        });
    }

    private void setupRoundedHeroImage(double radius) {
        if (heroImageHolder == null) return;

        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(heroImageHolder.widthProperty());
        clip.heightProperty().bind(heroImageHolder.heightProperty());
        heroImageHolder.setClip(clip);
    }

    private void loadRememberedUser() {
        boolean remember = prefs.getBoolean(PREF_REMEMBER, false);
        rememberCheck.setSelected(remember);
        if (remember) {
            usernameField.setText(prefs.get(PREF_USER, ""));
        }
    }

    private TextField activePasswordField() {
        return passwordShown ? passwordVisibleField : passwordField;
    }

    @FXML private void handleTogglePassword(ActionEvent event) {
        passwordShown = !passwordShown;

        passwordVisibleField.setVisible(passwordShown);
        passwordVisibleField.setManaged(passwordShown);
        passwordField.setVisible(!passwordShown);
        passwordField.setManaged(!passwordShown);

        togglePasswordBtn.setText(passwordShown ? "🙈" : "👁");

        TextField active = activePasswordField();
        active.requestFocus();
        active.positionCaret(active.getText().length());
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            AlertsUtil.warning(loginBtn, "Missing Information",
                    "Please enter your username and password.");
            (username.isEmpty() ? usernameField : activePasswordField()).requestFocus();
            return;
        }

        loginBtn.setDisable(true);
        loginBtn.setText("Signing in...");

        final long t0 = System.nanoTime(); // TEMP timing, remove once the slowness is found

        Thread worker = new Thread(() -> {
            AuthResult result = null;
            Exception failure = null;

            try {
                result = authenticate(username, password);
            } catch (Exception e) {
                failure = e;
            }

            System.out.println("[login] database + password check: " + ms(t0) + " ms");

            final AuthResult finalResult = result;
            final Exception finalFailure = failure;

            Platform.runLater(() -> {
                loginBtn.setDisable(false);
                loginBtn.setText(LOGIN_TEXT);

                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    AlertsUtil.error(loginBtn, "Login Error",
                            "Could not reach the database. Please try again.\n\nDetails: "
                                    + finalFailure.getMessage());
                    return;
                }

                switch (finalResult.status()) {
                    case SUCCESS -> {
                        saveRememberedUser(username);
                        onLoginSuccess(finalResult.user());
                        System.out.println("[login] next screen loaded and shown: " + ms(t0) + " ms total");
                    }
                    case INACTIVE -> {
                        AlertsUtil.warning(loginBtn, "Account Disabled",
                                "This account is inactive. Please contact your manager.");
                        passwordField.clear();
                    }
                    case INVALID -> {
                        AlertsUtil.warning(loginBtn, "Login Failed", "Incorrect username or password.");
                        passwordField.clear();
                        activePasswordField().requestFocus();
                    }
                }
            });
        }, "login-auth");
        worker.setDaemon(true);
        worker.start();
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private AuthResult authenticate(String username, String password) throws Exception {
        Optional<User> match = userDAO.authenticate(username, password);

        if (match.isEmpty()) {
            return new AuthResult(AuthStatus.INVALID, null);
        }

        User user = match.get();
        if (!user.isActive()) {
            return new AuthResult(AuthStatus.INACTIVE, null);
        }

        return new AuthResult(AuthStatus.SUCCESS, user);
    }

    private void recordLastLogin(User user) {
        Thread t = new Thread(() -> {
            try {
                userDAO.updateLastLogin(user.getUserId());
            } catch (Exception e) {
                System.err.println("LoginController: could not update last_login: " + e.getMessage());
            }
        }, "update-last-login");
        t.setDaemon(true);
        t.start();
    }

    private void onLoginSuccess(User user) {
        recordLastLogin(user);

        Stage stage = WindowUtil.stageOf(loginBtn);

        if (user.getRole() == AccountRole.CASHIER) {
            WindowUtil.showCashier(stage);
        } else {
            AdminController admin = WindowUtil.showAdmin(stage);
            if (admin != null) admin.setCurrentUser(user.getUsername(), user.getRole().getDisplayName());
        }
    }

    private void saveRememberedUser(String username) {
        if (rememberCheck.isSelected()) {
            prefs.putBoolean(PREF_REMEMBER, true);
            prefs.put(PREF_USER, username);
        } else {
            prefs.putBoolean(PREF_REMEMBER, false);
            prefs.remove(PREF_USER);
        }
    }
}