package com.cafe.kapibara.kapibara.controller.main.cashier;

import com.cafe.kapibara.kapibara.util.AlertsUtil;
import com.cafe.kapibara.kapibara.util.MessageBox;
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


public class CashierController implements Initializable, CashierShell {

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

    @FXML private Button navHome, navNewOrder, navTransactions;
    @FXML private Button navProfile, navSettings, navLogout;

    private final Map<Button, CashierPage> pageByButton = new LinkedHashMap<>();
    private CashierPage currentPage;
    private String userName = "User";
    private String userRole = "Cashier";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        pageByButton.put(navHome, CashierPage.HOME);
        pageByButton.put(navNewOrder, CashierPage.NEW_ORDER);
        pageByButton.put(navProfile, CashierPage.PROFILE);
        pageByButton.put(navTransactions, CashierPage.TRANSACTIONS);


        dateLabel.setText(LocalDate.now().format(TOP_DATE));
        loadLogo();

        Platform.runLater(() -> {
            if (currentPage == null) navigate(CashierPage.HOME);
        });
    }

    private void loadLogo() {
        if (sidebarLogo == null) return;
        try (InputStream in = getClass().getResourceAsStream(LOGO)) {
            if (in != null) sidebarLogo.setImage(new Image(in));
        } catch (IOException e) {
            System.err.println("CashierController: could not load logo: " + e.getMessage());
        }
    }

    private int userId;

    public void setCurrentUser(int id, String name, String role) {
        this.userId = id;
        this.userName = (name == null || name.isBlank()) ? "User" : name;
        this.userRole = (role == null || role.isBlank()) ? "Cashier" : role;
        userLabel.setText(userName + " · " + userRole + "  ▾");
    }

    @Override
    public int getCurrentUserId() {
        return userId;
    }

    // =====================================================================
    //  CashierShell
    // =====================================================================

    @Override
    public void navigate(CashierPage page) {
        URL fxml = getClass().getResource(page.getFxml());
        if (fxml == null) {
            AlertsUtil.error(root, "Page not found", "Missing file: " + page.getFxml());
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(fxml);
            Parent view = loader.load();

            Object controller = loader.getController();
            if (controller instanceof CashierShell.Aware aware) {
                aware.attach(this);
            }

            contentHolder.getChildren().setAll(view);
            currentPage = page;
            breadcrumbLabel.setText(BRANCH + " / " + (page == CashierPage.HOME
                    ? CashierShell.greeting() + ", " + CashierShell.firstName(userName) + "."
                    : page.getBreadcrumb()));
            highlightNav();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            AlertsUtil.error(root, "Unable to open page",
                    "Could not load: " + page.getFxml() + "\n" + e.getMessage());
        }
    }

    @Override
    public String getCurrentUserName() {
        return userName;
    }

    @Override
    public String getCurrentUserRole() {
        return userRole;
    }

    @Override
    public void logout() {
        CashierStore store = CashierStore.get();
        int unpaid = store.itemCount();
        String message = unpaid == 0
                ? "You'll return to the sign-in screen."
                : "The current order has " + unpaid + (unpaid == 1 ? " item" : " items")
                + " that haven't been paid. Logging out will discard it.";

        if (!AlertsUtil.confirm(root, AlertsUtil.Type.WARNING, "Log out?", message, "Log out")) {
            return;
        }

        store.clear();
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
        CashierPage page = pageByButton.get(event.getSource());
        if (page != null) navigate(page);
    }

    @FXML
    private void handleTransactions(ActionEvent event) {
        CashierPage page = pageByButton.get(event.getSource());
        if (page != null) navigate(page);
    }

    @FXML private void handleSettings(ActionEvent event)     { MessageBox.comingSoon(root, "Settings"); }
    @FXML private void handleLogout(ActionEvent event)       { logout(); }
}