package com.cafe.kapibara.kapibara.controller.main.admin;

import com.cafe.kapibara.kapibara.util.AlertsUtil;
import com.cafe.kapibara.kapibara.util.WindowUtil;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The admin shell: sidebar + top bar. Every admin page (home, transactions, sales,
 * inventory, accounts, profile) is loaded into {@link #contentHolder}.
 */
public class AdminController implements Initializable, AdminShell {

    private static final String BRANCH = "Caloocan branch";
    private static final String LOGO = "/com/cafe/kapibara/kapibara/images/logo.png";
    private static final DateTimeFormatter TOP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);

    @FXML private BorderPane root;
    @FXML private StackPane contentHolder;
    @FXML private ImageView sidebarLogo;

    @FXML private Label breadcrumbLabel;
    @FXML private Label dateLabel;
    @FXML private Label userLabel;

    @FXML private Button navHome, navTransactions, navSales, navInventory, navAccounts;
    @FXML private Button navProfile, navSettings, navLogout;

    private final Map<Button, AdminPage> pageByButton = new LinkedHashMap<>();
    private AdminPage currentPage;
    private String userName = "User";
    private String userRole = "";
    private Object pagePayload;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        pageByButton.put(navHome, AdminPage.HOME);
        pageByButton.put(navTransactions, AdminPage.TRANSACTIONS);
        pageByButton.put(navSales, AdminPage.SALES);
        pageByButton.put(navInventory, AdminPage.INVENTORY);
        pageByButton.put(navAccounts, AdminPage.ACCOUNTS);
        pageByButton.put(navProfile, AdminPage.PROFILE);

        dateLabel.setText(LocalDate.now().format(TOP_DATE));
        loadLogo();

        // Open the home page after the login code has called setCurrentUser(...),
        // so pages can greet the signed-in user by name.
        Platform.runLater(() -> {
            if (currentPage == null) navigate(AdminPage.HOME);
        });
    }

    private void loadLogo() {
        if (sidebarLogo == null) return;
        try (InputStream in = getClass().getResourceAsStream(LOGO)) {
            if (in != null) {
                sidebarLogo.setImage(new Image(in));
            }
            // Using the SVG instead? sidebarLogo.setImage(SvgUtil.load(".../images/logo.svg"));
        } catch (IOException e) {
            System.err.println("AdminController: could not load logo: " + e.getMessage());
        }
    }

    /** Call this right after login to show the signed-in user in the top bar. */
    public void setCurrentUser(String name, String role) {
        this.userName = (name == null || name.isBlank()) ? "User" : name;
        this.userRole = role == null ? "" : role;
        userLabel.setText(userRole.isEmpty()
                ? userName + "  ▾"
                : userName + " · " + userRole + "  ▾");
    }

    // =====================================================================
    //  AdminShell
    // =====================================================================

    @Override
    public void navigate(AdminPage page) {
        navigate(page, null);
    }

    @Override
    public void navigate(AdminPage page, Object payload) {
        this.pagePayload = payload;
        URL fxml = getClass().getResource(page.getFxml());
        if (fxml == null) {
            AlertsUtil.error(root, "Page not found", "Missing file: " + page.getFxml());
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(fxml);
            Parent view = loader.load();

            Object controller = loader.getController();
            if (controller instanceof AdminShell.Aware aware) {
                aware.attach(this);
            }

            contentHolder.getChildren().setAll(view);
            currentPage = page;
            breadcrumbLabel.setText(BRANCH + " / " + page.getBreadcrumb());
            highlightNav();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            AlertsUtil.error(root, "Unable to open page",
                    "Could not load: " + page.getFxml() + "\n" + e.getMessage());
        }
    }

    @Override
    public Object getPagePayload() {
        return pagePayload;
    }

    @Override
    public String getCurrentUserName() {
        return userName;
    }

    @Override
    public String getCurrentUserRole() {
        return userRole;
    }

    /** Signs out and returns to the login window. Used by the sidebar and the profile page. */
    @Override
    public void logout() {
        WindowUtil.showLogin(WindowUtil.stageOf(root));
    }

    private void highlightNav() {
        pageByButton.forEach((button, page) -> {
            button.getStyleClass().remove("adm-nav-active");
            if (currentPage != null && page == currentPage.getNavGroup()) {
                button.getStyleClass().add("adm-nav-active");
            }
        });
    }

    // =====================================================================
    //  Sidebar actions
    // =====================================================================

    @FXML
    private void handleNav(ActionEvent event) {
        AdminPage page = pageByButton.get(event.getSource());
        if (page != null) navigate(page);
    }

    @FXML private void handleProfile(ActionEvent event)  { navigate(AdminPage.PROFILE); }
    @FXML private void handleSettings(ActionEvent event) { /* TODO: settings page */ }

    @FXML
    private void handleLogout(ActionEvent event) {
        logout();
    }
}