package com.cafe.kapibara.kapibara.controller;

import com.cafe.kapibara.kapibara.config.DatabaseConnector;
import java.sql.*;

public class Test {
    public static void main(String[] args) throws Exception {
        try (Connection c = DatabaseConnector.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT item_id, name, unit, quantity FROM inventory_items")) {
            while (rs.next()) {
                System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | " + rs.getString(3) + " | " + rs.getDouble(4));
            }
        }
        DatabaseConnector.closePool();
    }
}
