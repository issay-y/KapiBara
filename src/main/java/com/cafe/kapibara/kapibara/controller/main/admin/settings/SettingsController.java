package com.cafe.kapibara.kapibara.controller.main.admin.settings;

import com.cafe.kapibara.kapibara.DAO.TransactionDAO;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;   // resolves the javafx Cell ambiguity
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class SettingsController {

    private static final Logger LOG = Logger.getLogger(SettingsController.class.getName());

    // Receipt
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextArea addressArea;
    @FXML private Label receiptStatus;

    // Export
    @FXML private ComboBox<Month> monthCombo;
    @FXML private ComboBox<Integer> yearCombo;
    @FXML private Button exportBtn;
    @FXML private Label exportStatus;

    // Security
    @FXML private ToggleButton pinSwitch;
    @FXML private VBox pinBox;
    @FXML private Label pinLabel;
    @FXML private PasswordField pinField;
    @FXML private PasswordField confirmPinField;
    @FXML private Label pinStatus;

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9][0-9 ()\\-]{6,19}$");
    private static final Pattern PIN   = Pattern.compile("^\\d{4,6}$");

    // Export columns (must match TransactionDAO.findForExport order)
    private static final String[] HEADERS = {
            "Date & time", "Order no.", "Cashier", "Items",
            "Subtotal (₱)", "VAT (₱)", "Discount (₱)", "Total (₱)", "Status"
    };
    private static final int TOTAL_COL = 7;
    private static final int STATUS_COL = 8;

    private boolean loadingSwitch = false;

    @FXML
    private void initialize() {
        loadReceipt();
        setupExport();
        setupSecurity();
    }

    // =====================================================================
    // Receipt information
    // =====================================================================
    private void loadReceipt() {
        phoneField.setText(SettingsService.getPhone());
        emailField.setText(SettingsService.getEmail());
        addressArea.setText(SettingsService.getAddress());
        status(receiptStatus, "", null);
    }

    @FXML
    private void handleReceiptReset() { loadReceipt(); }

    @FXML
    private void handleReceiptSave() {
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();
        String address = addressArea.getText().trim();

        if (!PHONE.matcher(phone).matches()) {
            status(receiptStatus, "Enter a valid contact number.", false); return;
        }
        if (!EMAIL.matcher(email).matches()) {
            status(receiptStatus, "Enter a valid email address.", false); return;
        }
        if (address.isEmpty()) {
            status(receiptStatus, "Address can't be empty.", false); return;
        }
        SettingsService.saveReceipt(phone, email, address);
        status(receiptStatus, "Receipt information saved.", true);
    }

    // =====================================================================
    // Export by month
    // =====================================================================
    private void setupExport() {
        monthCombo.getItems().addAll(Month.values());
        monthCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Month m) {
                return m == null ? "" : m.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            }
            @Override public Month fromString(String s) { return null; }
        });

        int thisYear = Year.now().getValue();
        for (int y = thisYear; y >= thisYear - 5; y--) yearCombo.getItems().add(y);

        monthCombo.setValue(LocalDate.now().getMonth());
        yearCombo.setValue(thisYear);
    }

    @FXML
    private void handleExport() {
        if (monthCombo.getValue() == null || yearCombo.getValue() == null) {
            status(exportStatus, "Choose a month and year first.", false); return;
        }
        YearMonth ym = YearMonth.of(yearCombo.getValue(), monthCombo.getValue());

        FileChooser fc = new FileChooser();
        fc.setTitle("Save sales export");
        fc.setInitialFileName("KapiBara-Sales-" + ym + ".xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbook (*.xlsx)", "*.xlsx"));
        File file = fc.showSaveDialog(exportBtn.getScene().getWindow());
        if (file == null) return;

        exportBtn.setDisable(true);
        status(exportStatus, "Exporting…", null);

        Task<Integer> task = new Task<>() {
            @Override protected Integer call() throws Exception {
                List<List<Object>> rows = new TransactionDAO().findForExport(ym);
                writeWorkbook(file, ym, rows);
                return rows.size();
            }
        };
        task.setOnSucceeded(e -> {
            exportBtn.setDisable(false);
            int n = task.getValue();
            status(exportStatus, n == 0
                    ? "No transactions found for that month. An empty sheet was saved."
                    : "Exported " + n + " transactions to " + file.getName() + ".", true);
        });
        task.setOnFailed(e -> {
            exportBtn.setDisable(false);
            LOG.log(Level.SEVERE, "Sales export failed", task.getException());
            status(exportStatus, "Export failed: " + task.getException().getMessage(), false);
        });
        Thread t = new Thread(task, "sales-export");
        t.setDaemon(true);
        t.start();
    }

    private void writeWorkbook(File file, YearMonth ym, List<List<Object>> rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(
                    ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear());

            Font bold = wb.createFont();
            bold.setBold(true);

            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.TAN.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            DataFormat fmt = wb.createDataFormat();
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(fmt.getFormat("#,##0.00"));

            CellStyle moneyBold = wb.createCellStyle();
            moneyBold.setDataFormat(fmt.getFormat("#,##0.00"));
            moneyBold.setFont(bold);

            Row hr = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = hr.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(head);
            }

            double total = 0;
            int r = 1;
            for (List<Object> row : rows) {
                Row xr = sheet.createRow(r++);
                for (int c = 0; c < row.size(); c++) {
                    Object v = row.get(c);
                    Cell cell = xr.createCell(c);
                    if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                        cell.setCellStyle(money);
                    } else {
                        cell.setCellValue(v == null ? "" : v.toString());
                    }
                }
                boolean completed = "COMPLETED".equalsIgnoreCase(String.valueOf(row.get(STATUS_COL)));
                if (completed && row.get(TOTAL_COL) instanceof Number n) total += n.doubleValue();
            }

            if (!rows.isEmpty()) {
                Row tr = sheet.createRow(r + 1);
                Cell label = tr.createCell(TOTAL_COL - 1);
                label.setCellValue("Total (completed)");
                label.setCellStyle(head);
                Cell sum = tr.createCell(TOTAL_COL);
                sum.setCellValue(total);
                sum.setCellStyle(moneyBold);
            }

            for (int c = 0; c < HEADERS.length; c++) sheet.autoSizeColumn(c);
            sheet.createFreezePane(0, 1);

            try (FileOutputStream out = new FileOutputStream(file)) {
                wb.write(out);
            }
        }
    }

    // =====================================================================
    // Void PIN
    // =====================================================================
    private void setupSecurity() {
        loadingSwitch = true;
        boolean on = SettingsService.isVoidPinRequired();
        pinSwitch.setSelected(on);
        pinSwitch.setText(on ? "ON" : "OFF");
        showPinBox(on);
        loadingSwitch = false;

        pinLabel.setText(SettingsService.hasVoidPin() ? "New PIN (4–6 digits)" : "PIN (4–6 digits)");

        pinSwitch.selectedProperty().addListener((obs, was, now) -> {
            if (loadingSwitch) return;
            pinSwitch.setText(now ? "ON" : "OFF");
            showPinBox(now);

            if (!now) {
                SettingsService.setVoidPinRequired(false);
                status(pinStatus, "PIN is no longer required for voiding.", true);
            } else if (SettingsService.hasVoidPin()) {
                SettingsService.setVoidPinRequired(true);
                status(pinStatus, "PIN is now required for voiding. Enter a new PIN below to change it.", true);
            } else {
                status(pinStatus, "Set a PIN below to turn this on.", null);
            }
        });
    }

    private void showPinBox(boolean show) {
        pinBox.setVisible(show);
        pinBox.setManaged(show);
    }

    @FXML
    private void handlePinSave() {
        String pin = pinField.getText();
        String confirm = confirmPinField.getText();

        if (!PIN.matcher(pin).matches()) {
            status(pinStatus, "PIN must be 4 to 6 digits.", false); return;
        }
        if (!pin.equals(confirm)) {
            status(pinStatus, "The two PINs don't match.", false); return;
        }
        SettingsService.saveVoidPin(pin);
        SettingsService.setVoidPinRequired(true);
        pinField.clear();
        confirmPinField.clear();
        pinLabel.setText("New PIN (4–6 digits)");
        status(pinStatus, "PIN saved. Voiding now requires it.", true);
    }

    // =====================================================================
    // Helpers
    // =====================================================================
    /** ok: true = green, false = red, null = neutral. */
    private void status(Label label, String text, Boolean ok) {
        label.setText(text);
        label.getStyleClass().removeAll("adm-sub-good", "adm-sub-bad");
        if (ok != null) label.getStyleClass().add(ok ? "adm-sub-good" : "adm-sub-bad");
    }
}