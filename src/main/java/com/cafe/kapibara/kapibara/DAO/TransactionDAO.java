package com.cafe.kapibara.kapibara.DAO;

import com.cafe.kapibara.kapibara.config.DatabaseConnector;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Sale;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.SaleLine;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionDAO {

    // =====================================================================
    //  WRITE: SAVE TRANSACTION
    // =====================================================================

    public void insertTransaction(Sale sale, int userId) throws SQLException {
        String insertTx = "INSERT INTO transactions (order_number, user_id, cashier_name, subtotal, vat, discount, "
                + "is_pwd, total_amount, cash_received, change_amount, status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?)";

        String insertItem = "INSERT INTO transaction_items (transaction_id, item_name, quantity, unit_price, line_total) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnector.getConnection()) {
            boolean oldAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                int transactionId;

                try (PreparedStatement ps = conn.prepareStatement(insertTx, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, sale.number());
                    ps.setInt(2, userId);
                    ps.setString(3, sale.cashier());
                    ps.setBigDecimal(4, sale.subtotal());
                    ps.setBigDecimal(5, sale.vat());
                    ps.setBigDecimal(6, sale.discount());
                    ps.setBoolean(7, sale.seniorPwd());
                    ps.setBigDecimal(8, sale.total());
                    ps.setBigDecimal(9, sale.cashReceived());
                    ps.setBigDecimal(10, sale.change());
                    ps.setTimestamp(11, Timestamp.valueOf(sale.time()));

                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) transactionId = rs.getInt(1);
                        else throw new SQLException("Failed to retrieve transaction_id");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(insertItem)) {
                    for (SaleLine line : sale.lines()) {
                        ps.setInt(1, transactionId);
                        ps.setString(2, line.name());
                        ps.setInt(3, line.qty());
                        ps.setBigDecimal(4, line.unitPrice());
                        ps.setBigDecimal(5, line.amount());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(oldAuto);
            }
        }
    }

    // =====================================================================
    //  READ: GET ALL TRANSACTIONS
    // =====================================================================

    public List<Sale> findAllTransactions() throws SQLException {
        List<Sale> sales = new ArrayList<>();
        Map<Integer, List<SaleLine>> itemsMap = new HashMap<>();

        try (Connection conn = DatabaseConnector.getConnection()) {
            String itemSql = "SELECT transaction_id, item_name, unit_price, quantity FROM transaction_items";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(itemSql)) {
                while (rs.next()) {
                    int txId = rs.getInt("transaction_id");
                    itemsMap.computeIfAbsent(txId, k -> new ArrayList<>())
                            .add(new SaleLine(
                                    rs.getString("item_name"),
                                    rs.getBigDecimal("unit_price"),
                                    rs.getInt("quantity")));
                }
            }

            String txSql = "SELECT * FROM transactions ORDER BY created_at ASC";

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(txSql)) {
                while (rs.next()) {
                    int txId = rs.getInt("transaction_id");
                    List<SaleLine> lines = itemsMap.getOrDefault(txId, new ArrayList<>());

                    int totalItems = lines.stream().mapToInt(SaleLine::qty).sum();
                    Timestamp time = rs.getTimestamp("created_at");

                    sales.add(new Sale(
                            rs.getString("order_number"),
                            time.toLocalDateTime(),
                            rs.getString("cashier_name"),
                            totalItems,
                            lines,
                            rs.getBigDecimal("subtotal"),
                            rs.getBigDecimal("vat"),
                            rs.getBigDecimal("discount"),
                            rs.getBoolean("is_pwd"),
                            rs.getBigDecimal("total_amount"),
                            rs.getBigDecimal("cash_received"),
                            rs.getBigDecimal("change_amount")
                    ));
                }
            }
        }
        return sales;
    }
}