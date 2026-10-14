package com.cafe.kapibara.kapibara.model.account;

/** The editable details of one account (used by the Add / Edit account form). */
public record AccountForm(long id, String fullName, String username,
                          AccountRole role, AccountStatus status) {
}