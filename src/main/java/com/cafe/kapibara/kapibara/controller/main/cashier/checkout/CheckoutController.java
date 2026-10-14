package com.cafe.kapibara.kapibara.controller.main.cashier.checkout;

import com.cafe.kapibara.kapibara.controller.main.cashier.CashierPage;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Line;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import com.cafe.kapibara.kapibara.util.MessageBox;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.money;
import static com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.moneyShort;

/** "Cash payment": review the order, apply the PWD / Senior discount, take cash, give change. */
public class CheckoutController implements CashierShell.Aware {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final BigDecimal STEP = new BigDecimal("500");

    @FXML private Label orderNoLabel, orderTimeLabel;
    @FXML private GridPane summaryGrid;
    @FXML private Label subtotalLabel, vatCaption, vatLabel, discountCaption, discountLabel, totalLabel;
    @FXML private Label cashierLabel, dateLabel, noteLabel;

    @FXML private Label dueLabel, discountAmountLabel;
    @FXML private HBox discountRow;
    @FXML private CheckBox discountCheck;
    @FXML private TextField cashField;
    @FXML private HBox quickRow, statusBox;
    @FXML private Label changeCaption, changeLabel, statusIcon, statusTitle, statusSub;
    @FXML private Button confirmBtn;

    private final CashierStore store = CashierStore.get();
    private final Map<Button, BigDecimal> quickAmounts = new LinkedHashMap<>();
    private CashierShell shell;
    private BigDecimal cash; // parsed cash field, null when empty/invalid

    @FXML
    private void initialize() {
        cashField.textProperty().addListener((obs, old, text) -> updateCash());
    }

    @Override
    public void attach(CashierShell shell) {
        this.shell = shell;

        // Nothing to pay for (for example the page was reopened after a sale): go back.
        if (store.isEmpty()) {
            Platform.runLater(() -> shell.navigate(CashierPage.NEW_ORDER));
            return;
        }

        orderNoLabel.setText("#" + store.currentOrderNumber());
        orderTimeLabel.setText(store.orderTime().format(TIME));
        cashierLabel.setText(shell.getCurrentUserName());
        dateLabel.setText(LocalDate.now().format(DAY));

        renderLines();
        discountCheck.setSelected(store.isSeniorPwd());
        renderTotals();
        renderQuick();
        updateCash();
        Platform.runLater(cashField::requestFocus);
    }

    // ------------------------------------------------------------------
    // Order summary
    // ------------------------------------------------------------------

    private void renderLines() {
        summaryGrid.getChildren().clear();
        summaryGrid.add(cell("Item", "adm-th", Pos.CENTER_LEFT), 0, 0);
        summaryGrid.add(cell("Qty", "adm-th", Pos.CENTER), 1, 0);
        summaryGrid.add(cell("Amount", "adm-th", Pos.CENTER_RIGHT), 2, 0);

        int row = 1;
        for (Line line : store.lines()) {
            Product p = line.getProduct();
            summaryGrid.add(cell(p.getName() + " · " + moneyShort(p.getPrice()) + " each", "adm-td", Pos.CENTER_LEFT), 0, row);
            summaryGrid.add(cell(String.valueOf(line.getQty()), "adm-td", Pos.CENTER), 1, row);
            summaryGrid.add(cell(money(line.lineTotal()), "adm-td", Pos.CENTER_RIGHT), 2, row);
            row++;
        }
    }

    private static Label cell(String text, String styleClass, Pos alignment) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(alignment);
        GridPane.setHgrow(l, Priority.ALWAYS);
        return l;
    }

    /** Totals depend on the discount, so this runs again whenever the check box changes. */
    private void renderTotals() {
        boolean pwd = store.isSeniorPwd();
        String discountText = pwd ? "−" + money(store.discount()) : money(store.discount());

        subtotalLabel.setText(money(store.subtotal()));
        vatCaption.setText(pwd ? "VAT (exempt)" : "VAT (12%, included)");
        vatLabel.setText(money(store.vat()));
        discountCaption.setText(pwd ? "Discount (PWD / Senior 20%)" : "Discount");
        discountLabel.setText(discountText);
        totalLabel.setText(money(store.total()));
        dueLabel.setText(money(store.total()));

        discountAmountLabel.setText(pwd ? discountText : "");
        discountRow.getStyleClass().remove("csh-check-row-on");
        if (pwd) discountRow.getStyleClass().add("csh-check-row-on");

        int n = store.itemCount();
        noteLabel.setText(n + (n == 1 ? " item" : " items") + " • "
                + (pwd ? "VAT-exempt. 20% PWD / Senior Citizen discount applied to this order."
                : "All prices include 12% VAT. No discount applied to this order."));
    }

    // ------------------------------------------------------------------
    // Cash entry
    // ------------------------------------------------------------------

    /** Exact amount, plus the next two round bills above the total (for ₱385: ₱500 and ₱1,000). */
    private void renderQuick() {
        quickRow.getChildren().clear();
        quickAmounts.clear();

        BigDecimal total = store.total();
        BigDecimal first = total.divide(STEP, 0, RoundingMode.CEILING).multiply(STEP);
        if (first.compareTo(total) == 0) first = first.add(STEP);
        BigDecimal second = first.compareTo(new BigDecimal("1000")) < 0 ? new BigDecimal("1000") : first.add(STEP);

        addQuick("Exact", total);
        addQuick("₱" + String.format(Locale.US, "%,d", first.intValue()), first);
        addQuick("₱" + String.format(Locale.US, "%,d", second.intValue()), second);
    }

    private void addQuick(String text, BigDecimal amount) {
        Button b = new Button(text);
        b.getStyleClass().add("csh-quick");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setFocusTraversable(false);
        HBox.setHgrow(b, Priority.ALWAYS);
        b.setOnAction(e -> {
            cashField.setText(amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
            cashField.requestFocus();
            cashField.end();
        });
        quickAmounts.put(b, amount);
        quickRow.getChildren().add(b);
    }

    /** Refreshes the field colour, change, status banner and quick buttons. */
    private void updateCash() {
        BigDecimal total = store.total();
        cash = CashierStore.parseMoney(cashField.getText());

        cashField.getStyleClass().removeAll("csh-cash-ok", "csh-cash-short");
        quickAmounts.forEach((button, amount) -> {
            button.getStyleClass().remove("csh-quick-on");
            if (cash != null && cash.compareTo(amount) == 0) button.getStyleClass().add("csh-quick-on");
        });

        if (cash == null) {
            changeCaption.setText("Change to give");
            changeLabel.setText(money(BigDecimal.ZERO));
            changeLabel.getStyleClass().setAll("csh-change-value", "csh-change-idle");
            setStatus("idle", "…", "Enter the cash received",
                    "Type the amount or tap a quick button.");
        } else if (cash.compareTo(total) >= 0) {
            BigDecimal change = cash.subtract(total);
            cashField.getStyleClass().add("csh-cash-ok");
            changeCaption.setText("Change to give");
            changeLabel.setText(money(change));
            changeLabel.getStyleClass().setAll("csh-change-value");
            setStatus("ok", "✓", "Ready to complete payment",
                    change.signum() == 0 ? "Exact amount received. No change to return."
                            : "Return " + money(change) + " change to the customer.");
        } else {
            BigDecimal missing = total.subtract(cash);
            cashField.getStyleClass().add("csh-cash-short");
            changeCaption.setText("Still needed");
            changeLabel.setText(money(missing));
            changeLabel.getStyleClass().setAll("csh-change-value", "csh-change-short");
            setStatus("warn", "!", "Not enough cash yet", money(missing) + " more is needed to cover the total.");
        }
    }

    private void setStatus(String kind, String icon, String title, String sub) {
        statusBox.getStyleClass().setAll("csh-status", "csh-status-" + kind);
        statusIcon.setText(icon);
        statusTitle.setText(title);
        statusSub.setText(sub);
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    /** The PWD / Senior Citizen check box. Asking first stops an accidental tick. */
    @FXML
    private void handleDiscount() {
        if (discountCheck.isSelected()) {
            boolean ok = MessageBox.confirm(discountCheck, "Apply PWD / Senior discount?",
                    "Apply the 20% discount (and VAT exemption) only if the customer shows a valid "
                            + "PWD or Senior Citizen ID.",
                    "Apply discount", "Not now", false);
            if (!ok) {
                discountCheck.setSelected(false);
                return;
            }
        }
        store.setSeniorPwd(discountCheck.isSelected());
        renderTotals();
        renderQuick();
        updateCash();
    }

    @FXML
    private void handleBack() {
        shell.navigate(CashierPage.NEW_ORDER);
    }

    @FXML
    private void handleCancel() {
        if (MessageBox.confirm(confirmBtn, "Cancel this payment?",
                "No money is taken. The order stays open and you'll go back to it.",
                "Cancel payment", "Stay here", true)) {
            shell.navigate(CashierPage.NEW_ORDER);
        }
    }

    @FXML
    private void handleConfirm() {
        BigDecimal total = store.total();

        if (cash == null) {
            MessageBox.info(confirmBtn, "Enter the cash received", "Type the amount the customer handed over first.");
            cashField.requestFocus();
            return;
        }
        if (cash.compareTo(total) < 0) {
            MessageBox.info(confirmBtn, "Not enough cash",
                    "The customer still needs to hand over " + money(total.subtract(cash)) + ".");
            cashField.requestFocus();
            return;
        }

        BigDecimal change = cash.subtract(total);
        String message = "Receive " + money(cash) + " for a total of " + money(total) + ".\n"
                + (change.signum() > 0 ? "Return " + money(change) + " change to the customer."
                : "No change to return.");
        if (!MessageBox.confirm(confirmBtn, "Complete payment?", message, "Confirm payment", "Go back", false)) {
            return;
        }

        try {
            store.checkout(cash, shell.getCurrentUserName());
            shell.navigate(CashierPage.RECEIPT);
        } catch (IllegalStateException ex) {
            MessageBox.info(confirmBtn, "Couldn't complete the order", ex.getMessage());
            shell.navigate(CashierPage.NEW_ORDER);
        }
    }
}