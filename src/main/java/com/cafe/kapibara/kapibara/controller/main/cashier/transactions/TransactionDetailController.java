    package com.cafe.kapibara.kapibara.controller.main.cashier.transactions;

    import com.cafe.kapibara.kapibara.controller.main.cashier.CashierPage;
    import com.cafe.kapibara.kapibara.controller.main.cashier.CashierShell;
    import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore;
    import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
    import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.SaleLine;
    import javafx.application.Platform;
    import javafx.fxml.FXML;
    import javafx.geometry.Pos;
    import javafx.scene.control.Label;
    import javafx.scene.layout.GridPane;
    import javafx.scene.layout.HBox;
    import javafx.scene.layout.Priority;

    import java.time.format.DateTimeFormatter;
    import java.util.Locale;
    import java.util.Optional;

    import static com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.money;

    /** "Transaction details": one completed sale, opened from the Transactions list or a receipt. */
    public class TransactionDetailController implements CashierShell.Aware {

        private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
        private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a", Locale.ENGLISH);

        @FXML private Label subLabel;
        @FXML private HBox pillRow;
        @FXML private GridPane itemsGrid;
        @FXML private Label itemsNote;
        @FXML private Label infoNumber, infoTime, infoCashier, infoBranch;
        @FXML private Label subtotalLabel, vatCaption, vatLabel, discountCaption, discountLabel, totalLabel;
        @FXML private Label cashLabel, changeLabel;

        private CashierShell shell;
        private Sale sale;

        @Override
        public void attach(CashierShell shell) {
            this.shell = shell;

            Optional<Sale> viewed = CashierStore.get().viewedSale();
            if (viewed.isEmpty()) {
                Platform.runLater(() -> shell.navigate(CashierPage.TRANSACTIONS));
                return;
            }
            sale = viewed.get();
            render();
        }

        private void render() {
            subLabel.setText(sale.number() + " • Completed on " + sale.time().format(LONG_DAY)
                    + " at " + sale.time().format(CLOCK));

            pillRow.getChildren().setAll(pill("Complete", "adm-pill-green"), pill("Paid", "adm-pill-green"),
                    pill("Cash payment", "adm-pill-tan"));
            if (sale.seniorPwd()) pillRow.getChildren().add(pill("PWD / Senior 20%", "adm-pill-gold"));

            renderItems();

            infoNumber.setText(sale.number());
            infoTime.setText(sale.time().format(STAMP));
            infoCashier.setText(sale.cashier());
            infoBranch.setText(CashierStore.BRANCH_SHORT + " / " + CashierStore.COUNTER);

            boolean pwd = sale.seniorPwd();
            subtotalLabel.setText(money(sale.subtotal()));
            vatCaption.setText(pwd ? "VAT (exempt)" : "VAT (12%, included)");
            vatLabel.setText(money(sale.vat()));
            discountCaption.setText(pwd ? "Discount (PWD / Senior 20%)" : "Discount");
            discountLabel.setText(pwd ? "−" + money(sale.discount()) : money(sale.discount()));
            totalLabel.setText(money(sale.total()));
            cashLabel.setText(money(sale.cashReceived()));
            changeLabel.setText(money(sale.change()));
        }

        private void renderItems() {
            itemsGrid.getChildren().clear();
            itemsGrid.add(cell("Product", "adm-th"), 0, 0);
            itemsGrid.add(cell("Quantity", "adm-th"), 1, 0);
            itemsGrid.add(cell("Unit price", "adm-th"), 2, 0);
            itemsGrid.add(cell("Line total", "adm-th"), 3, 0);

            int row = 1;
            for (SaleLine line : sale.lines()) {
                itemsGrid.add(cell(line.name(), "adm-td"), 0, row);
                itemsGrid.add(cell(String.valueOf(line.qty()), "adm-td"), 1, row);
                itemsGrid.add(cell(money(line.unitPrice()), "adm-td"), 2, row);
                itemsGrid.add(cell(money(line.amount()), "adm-td"), 3, row);
                row++;
            }

            int products = sale.lines().size();
            itemsNote.setText(sale.items() + (sale.items() == 1 ? " item" : " items") + " across "
                    + products + (products == 1 ? " product" : " products"));
        }

        private static Label cell(String text, String styleClass) {
            Label l = new Label(text);
            l.getStyleClass().add(styleClass);
            l.setMaxWidth(Double.MAX_VALUE);
            l.setAlignment(Pos.CENTER_LEFT);
            GridPane.setHgrow(l, Priority.ALWAYS);
            return l;
        }

        private static Label pill(String text, String styleClass) {
            Label l = new Label(text);
            l.getStyleClass().add(styleClass);
            return l;
        }

        @FXML
        private void handleReceipt() {
            CashierStore store = CashierStore.get();
            store.setViewedSale(sale);
            store.setReceiptReturn(CashierPage.TRANSACTION_DETAIL);
            shell.navigate(CashierPage.RECEIPT);
        }

        @FXML
        private void handleBack() {
            shell.navigate(CashierPage.TRANSACTIONS);
        }
    }