package com.cafe.kapibara.kapibara.DAO;

import com.cafe.kapibara.kapibara.config.DatabaseConnector;
import com.cafe.kapibara.kapibara.model.account.AccountForm;
import com.cafe.kapibara.kapibara.model.account.AccountRole;
import com.cafe.kapibara.kapibara.model.account.AccountRow;
import com.cafe.kapibara.kapibara.model.account.AccountStatus;
import com.cafe.kapibara.kapibara.util.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Queries for the admin "Team accounts" pages. Uses the same users table as UserDAO. */
public class AccountDAO {

    // =====================================================================
    //  READ
    // =====================================================================

    public List<AccountRow> findAll() throws SQLException {
        String sql = "SELECT user_id, full_name, username, role, is_active, last_login "
                + "FROM users ORDER BY user_id";

        List<AccountRow> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Timestamp lastLogin = rs.getTimestamp("last_login");
                rows.add(new AccountRow(
                        rs.getLong("user_id"),
                        rs.getString("full_name"),
                        rs.getString("username"),
                        AccountRole.fromString(rs.getString("role")),
                        rs.getBoolean("is_active") ? AccountStatus.ACTIVE : AccountStatus.INACTIVE,
                        lastLogin == null ? null : lastLogin.toLocalDateTime(),
                        null // created date isn't needed on these pages
                ));
            }
        }
        return rows;
    }

    public Optional<AccountForm> findById(long id) throws SQLException {
        String sql = "SELECT user_id, full_name, username, role, is_active FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new AccountForm(
                        rs.getLong("user_id"),
                        rs.getString("full_name"),
                        rs.getString("username"),
                        AccountRole.fromString(rs.getString("role")),
                        rs.getBoolean("is_active") ? AccountStatus.ACTIVE : AccountStatus.INACTIVE));
            }
        }
    }

    /** Case-insensitive check. Pass the account's own id as excludeId when editing (0 when adding). */
    public boolean usernameExists(String username, long excludeId) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?) AND user_id <> ? LIMIT 1";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setLong(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // =====================================================================
    //  WRITE
    // =====================================================================

    /** Inserts a new account. The plain password is hashed before it is stored. */
    public void insert(AccountForm form, String plainPassword) throws SQLException {
        String sql = "INSERT INTO users (username, password, role, full_name, is_active) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.username());
            ps.setString(2, PasswordHasher.hash(plainPassword));
            ps.setString(3, form.role().name());
            ps.setString(4, form.fullName());
            ps.setBoolean(5, form.status() == AccountStatus.ACTIVE);
            ps.executeUpdate();
        }
    }

    /** Updates an account. Pass newPlainPassword = null to keep the current password. */
    public void update(AccountForm form, String newPlainPassword) throws SQLException {
        boolean changePassword = newPlainPassword != null && !newPlainPassword.isEmpty();
        String sql = changePassword
                ? "UPDATE users SET username = ?, full_name = ?, role = ?, is_active = ?, password = ? WHERE user_id = ?"
                : "UPDATE users SET username = ?, full_name = ?, role = ?, is_active = ? WHERE user_id = ?";

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, form.username());
            ps.setString(2, form.fullName());
            ps.setString(3, form.role().name());
            ps.setBoolean(4, form.status() == AccountStatus.ACTIVE);
            if (changePassword) {
                ps.setString(5, PasswordHasher.hash(newPlainPassword));
                ps.setLong(6, form.id());
            } else {
                ps.setLong(5, form.id());
            }
            ps.executeUpdate();
        }
    }

    /**
     * Deletes an account. Admin accounts are permanent: the query itself refuses to delete them,
     * so this can't happen even if the UI check is bypassed.
     *
     * @return true if a row was deleted
     */
    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM users WHERE user_id = ? AND UPPER(role) <> 'ADMIN'";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setQueryTimeout(5);
            return ps.executeUpdate() > 0;
        }
    }
}