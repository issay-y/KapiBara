package com.cafe.kapibara.kapibara.controller.main.admin.account;

import com.cafe.kapibara.kapibara.DAO.AccountDAO;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminPage;
import com.cafe.kapibara.kapibara.controller.main.admin.AdminShell;
import com.cafe.kapibara.kapibara.model.account.AccountRole;
import com.cafe.kapibara.kapibara.model.account.AccountRow;
import com.cafe.kapibara.kapibara.model.account.AccountStatus;
import com.cafe.kapibara.kapibara.util.AlertsUtil;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TreeSet;

public class AccountController implements Initializable, AdminShell.Aware {

    private static final int PAGE_SIZE = 10;
    private static final String ALL_ROLES = "All roles";
    private static final String ALL_STATUSES = "All account statuses";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM · h:mm a", Locale.ENGLISH);

    // KPI cards
    @FXML private Label totalValue, totalSub;
    @FXML private Label activeValue, activeSub;
    @FXML private Label cashierValue, cashierSub;

    // filters
    @FXML private TextField searchField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private ComboBox<String> statusCombo;

    // table + paging
    @FXML private GridPane accountsGrid;
    @FXML private Label showingLabel;
    @FXML private HBox paginationBox;

    private final AccountDAO accountDAO = new AccountDAO();
    private final List<AccountRow> allRows = new ArrayList<>();
    private List<AccountRow> filtered = List.of();
    private int page = 0;
    private AdminShell shell;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        roleCombo.getItems().add(ALL_ROLES);
        for (AccountRole role : AccountRole.values()) {
            roleCombo.getItems().add(role.getDisplayName());
        }
        roleCombo.getSelectionModel().selectFirst();

        statusCombo.getItems().add(ALL_STATUSES);
        for (AccountStatus status : AccountStatus.values()) {
            statusCombo.getItems().add(status.getDisplayName());
        }
        statusCombo.getSelectionModel().selectFirst();

        searchField.textProperty().addListener((obs, old, now) -> applyFilters());
        roleCombo.valueProperty().addListener((obs, old, now) -> applyFilters());
        statusCombo.valueProperty().addListener((obs, old, now) -> applyFilters());

        loadAccounts();
    }

    @Override
    public void attach(AdminShell shell) {
        this.shell = shell;
    }

    // =====================================================================
    //  LOADING (database work happens off the UI thread)
    // =====================================================================

    private void loadAccounts() {
        Thread worker = new Thread(() -> {
            List<AccountRow> rows = null;
            Throwable failure = null;
            try {
                rows = accountDAO.findAll();
            } catch (Throwable t) {
                failure = t;
            }

            final List<AccountRow> finalRows = rows;
            final Throwable finalFailure = failure;

            Platform.runLater(() -> {
                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    showLoadError();
                    return;
                }
                allRows.clear();
                allRows.addAll(finalRows);
                loadKpis();
                applyFilters();
            });
        }, "load-accounts");
        worker.setDaemon(true);
        worker.start();
    }

    private void showLoadError() {
        accountsGrid.getChildren().clear();
        Label error = new Label("Could not load accounts. Please make sure the database is running.");
        error.getStyleClass().add("adm-td");
        error.setMaxWidth(Double.MAX_VALUE);
        accountsGrid.add(error, 0, 0, 6, 1);
        showingLabel.setText("");
        paginationBox.getChildren().clear();
    }

    // =====================================================================
    //  KPIs
    // =====================================================================

    private void loadKpis() {
        int total = allRows.size();
        int active = 0;
        int cashiers = 0;
        int cashiersActive = 0;
        for (AccountRow row : allRows) {
            boolean isActive = row.status() == AccountStatus.ACTIVE;
            if (isActive) active++;
            if (row.role() == AccountRole.CASHIER) {
                cashiers++;
                if (isActive) cashiersActive++;
            }
        }
        long roles = allRows.stream().map(AccountRow::role).distinct().count();

        totalValue.setText(String.valueOf(total));
        totalSub.setText("Caloocan branch team");

        activeValue.setText(String.valueOf(active));
        activeSub.setText(roles + " workspace role" + (roles == 1 ? "" : "s") + " across the team");

        cashierValue.setText(String.valueOf(cashiers));
        cashierSub.setText(cashiersActive + " active · " + (cashiers - cashiersActive) + " inactive");
    }

    // =====================================================================
    //  FILTER + TABLE
    // =====================================================================

    private void applyFilters() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String role = roleCombo.getValue();
        String status = statusCombo.getValue();

        filtered = allRows.stream()
                .filter(r -> q.isEmpty()
                        || safe(r.fullName()).toLowerCase(Locale.ROOT).contains(q)
                        || safe(r.username()).toLowerCase(Locale.ROOT).contains(q))
                .filter(r -> role == null || ALL_ROLES.equals(role) || r.role().getDisplayName().equals(role))
                .filter(r -> status == null || ALL_STATUSES.equals(status) || r.status().getDisplayName().equals(status))
                .toList();

        page = 0;
        render();
    }

    private void render() {
        accountsGrid.getChildren().clear();

        String[] headers = {"Name", "Username", "Role", "Status", "Last activity", "Actions"};
        for (int c = 0; c < headers.length; c++) {
            Label h = new Label(headers[c]);
            h.getStyleClass().add("adm-th");
            h.setMaxWidth(Double.MAX_VALUE);
            accountsGrid.add(h, c, 0);
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, filtered.size());

        if (filtered.isEmpty()) {
            Label empty = new Label("No accounts match your filters.");
            empty.getStyleClass().add("adm-td");
            empty.setMaxWidth(Double.MAX_VALUE);
            accountsGrid.add(empty, 0, 1, headers.length, 1);
            showingLabel.setText("No accounts to show");
        } else {
            for (int i = from; i < to; i++) {
                addRow(i - from + 1, filtered.get(i));
            }
            showingLabel.setText(filtered.size() <= PAGE_SIZE
                    ? "Showing all " + filtered.size() + " account" + (filtered.size() == 1 ? "" : "s")
                    : "Showing " + (from + 1) + "–" + to + " of " + filtered.size() + " accounts");
        }

        buildPagination();
    }

    private void addRow(int row, AccountRow account) {
        accountsGrid.add(cell(safe(account.fullName())), 0, row);
        accountsGrid.add(cell(safe(account.username())), 1, row);
        accountsGrid.add(cell(account.role().getDisplayName()), 2, row);

        Label pill = new Label(account.status().getDisplayName());
        pill.getStyleClass().add(account.status() == AccountStatus.ACTIVE ? "adm-pill-green" : "adm-pill-tan");
        accountsGrid.add(wrap(pill), 3, row);

        accountsGrid.add(cell(lastActivityText(account.lastLogin())), 4, row);

        Button edit = new Button("Edit");
        edit.getStyleClass().add("adm-link");
        edit.setOnAction(e -> handleEdit(account));

        Button delete = new Button("Delete");
        delete.getStyleClass().add("adm-link");
        delete.setOnAction(e -> handleDelete(account));

        HBox actions = new HBox(16, edit, delete);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("adm-td");
        accountsGrid.add(actions, 5, row);
    }

    private static Label cell(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("adm-td");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static HBox wrap(Node node) {
        HBox box = new HBox(node);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("adm-td");
        return box;
    }

    private static String lastActivityText(LocalDateTime lastLogin) {
        if (lastLogin == null) return "Never";
        if (lastLogin.toLocalDate().equals(LocalDate.now())) return "Today · " + lastLogin.format(TIME);
        return lastLogin.format(DATE_TIME);
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    // =====================================================================
    //  PAGINATION
    // =====================================================================

    private void buildPagination() {
        paginationBox.getChildren().clear();

        int totalPages = (int) Math.ceil(filtered.size() / (double) PAGE_SIZE);
        if (totalPages == 0) return;
        int last = totalPages - 1;

        Button prev = new Button("Previous");
        prev.getStyleClass().add("adm-page-btn");
        prev.setDisable(page == 0);
        prev.setOnAction(e -> goTo(page - 1));
        paginationBox.getChildren().add(prev);

        TreeSet<Integer> shown = new TreeSet<>();
        shown.add(0);
        shown.add(last);
        for (int p = page - 1; p <= page + 1; p++) {
            if (p >= 0 && p <= last) shown.add(p);
        }

        int previous = -1;
        for (int p : shown) {
            if (previous != -1 && p - previous > 1) {
                Label dots = new Label("…");
                dots.getStyleClass().add("adm-ellipsis");
                paginationBox.getChildren().add(dots);
            }
            Button number = new Button(String.valueOf(p + 1));
            number.getStyleClass().add(p == page ? "adm-page-num-active" : "adm-page-num");
            number.setOnAction(e -> goTo(p));
            paginationBox.getChildren().add(number);
            previous = p;
        }

        Button next = new Button("Next");
        next.getStyleClass().add("adm-page-btn");
        next.setDisable(page == last);
        next.setOnAction(e -> goTo(page + 1));
        paginationBox.getChildren().add(next);
    }

    private void goTo(int newPage) {
        page = newPage;
        render();
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================

    @FXML
    private void handleAddAccount(ActionEvent event) {
        if (shell != null) shell.navigate(AdminPage.ACCOUNT_ADD);
    }

    private void handleEdit(AccountRow account) {
        if (shell != null) shell.navigate(AdminPage.ACCOUNT_EDIT, account.id());
    }

    private void handleDelete(AccountRow account) {
        // The admin account is permanent.
        if (account.role() == AccountRole.ADMIN) {
            AlertsUtil.warning(accountsGrid, "Admin account is permanent",
                    "The admin account can't be deleted.");
            return;
        }

        if (isSignedInUser(account)) {
            AlertsUtil.warning(accountsGrid, "Can't delete your own account",
                    "You are signed in with this account. Ask an admin to delete it.");
            return;
        }

        boolean confirmed = AlertsUtil.confirm(accountsGrid, AlertsUtil.Type.WARNING, "Delete this account?",
                "Delete " + safe(account.fullName()) + " (" + safe(account.username()) + ")?\n"
                        + "This can't be undone.",
                "Delete");
        if (!confirmed) return;

        Thread worker = new Thread(() -> {
            boolean deleted = false;
            Throwable failure = null;
            try {
                deleted = accountDAO.delete(account.id());
            } catch (Throwable t) {
                failure = t;
            }

            final boolean finalDeleted = deleted;
            final Throwable finalFailure = failure;
            Platform.runLater(() -> {
                if (finalFailure != null) {
                    finalFailure.printStackTrace();
                    if (isConstraintViolation(finalFailure)) {
                        AlertsUtil.warning(accountsGrid, "Account can't be deleted",
                                "This account has recorded activity (such as sales), so it can't be deleted. "
                                        + "Edit it and set its status to Inactive instead.");
                    } else {
                        AlertsUtil.warning(accountsGrid, "Delete failed",
                                "Could not delete the account: " + finalFailure.getMessage());
                    }
                } else if (!finalDeleted) {
                    AlertsUtil.warning(accountsGrid, "Account not deleted",
                            "This account can't be deleted, or it no longer exists.");
                    loadAccounts();
                } else {
                    loadAccounts();
                }
            });
        }, "delete-account");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean isConstraintViolation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLIntegrityConstraintViolationException) return true;
        }
        return false;
    }

    /** The shell may hold either the username or the full name, so check both. */
    private boolean isSignedInUser(AccountRow account) {
        if (shell == null) return false;
        String current = safe(shell.getCurrentUserName());
        return !current.isBlank()
                && (current.equalsIgnoreCase(safe(account.username()))
                || current.equalsIgnoreCase(safe(account.fullName())));
    }
}