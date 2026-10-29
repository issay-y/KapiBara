package com.cafe.kapibara.kapibara.controller.main.cashier.receipt;

import com.cafe.kapibara.kapibara.controller.main.cashier.CashierPage;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierShell;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.SaleLine;
import com.cafe.kapibara.kapibara.controller.main.cashier.ProductCards;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import static com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.money;

/** "Payment complete": the virtual receipt for the sale that was just rung up. */
public class ReceiptController implements CashierShell.Aware {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a", Locale.ENGLISH);

    @FXML private Label branchLabel, addressLabel, numberLabel, timeLabel, cashierLabel;
    @FXML private VBox linesBox;
    @FXML private Label subtotalLabel, vatCaption, vatLabel, discountCaption, discountLabel, totalLabel;
    @FXML private Label cashLabel, changeLabel;
    @FXML private Label summaryLabel, changeBanner;

    public static final String BRANCH_SHORT = "Caloocan";

    private CashierShell shell;
    private Sale sale;

    @Override
    public void attach(CashierShell shell) {
        this.shell = shell;

        Optional<Sale> last = CashierStore.get().lastSale();
        if (last.isEmpty()) {
            // Opened without a finished sale: nothing to show.
            Platform.runLater(() -> shell.navigate(CashierPage.NEW_ORDER));
            return;
        }
        sale = last.get();
        render();
    }

    private void render() {
        branchLabel.setText("Coffee Shop • " + CashierStore.BRANCH_NAME);
        addressLabel.setText(CashierStore.BRANCH_ADDRESS);

        numberLabel.setText(sale.number());
        timeLabel.setText(sale.time().format(STAMP));
        cashierLabel.setText(sale.cashier());

        linesBox.getChildren().clear();
        for (SaleLine line : sale.lines()) {
            HBox top = new HBox(ProductCards.label(line.name(), "csh-receipt-name"),
                    ProductCards.spacer(),
                    ProductCards.label(money(line.amount()), "csh-receipt-amt"));
            top.setAlignment(Pos.CENTER_LEFT);
            linesBox.getChildren().add(new VBox(2, top,
                    ProductCards.label(line.qty() + " × " + money(line.unitPrice()), "csh-line-each")));
        }

        boolean pwd = sale.seniorPwd();
        subtotalLabel.setText(money(sale.subtotal()));
        vatCaption.setText(pwd ? "VAT (exempt)" : "VAT (12%, included)");
        vatLabel.setText(money(sale.vat()));
        discountCaption.setText(pwd ? "Discount (PWD / Senior 20%)" : "Discount");
        discountLabel.setText(pwd ? "−" + money(sale.discount()) : money(sale.discount()));
        totalLabel.setText(money(sale.total()));

        cashLabel.setText(money(sale.cashReceived()));
        changeLabel.setText(money(sale.change()));

        summaryLabel.setText(money(sale.total()) + " received for " + sale.items()
                + (sale.items() == 1 ? " item" : " items") + ". The transaction is marked complete.");
        changeBanner.setText(sale.change().signum() > 0
                ? "Change returned: " + money(sale.change())
                : "Exact amount received");
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    @FXML
    private void handleNewOrder() {
        shell.navigate(CashierPage.NEW_ORDER);
    }

    /** Opens the Transactions page, the same as the "Transactions" item in the sidebar. */

    @FXML
    private void handleClose() {
        shell.navigate(CashierPage.NEW_ORDER);
    }
}