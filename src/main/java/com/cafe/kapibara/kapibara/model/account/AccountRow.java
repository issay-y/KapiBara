package com.cafe.kapibara.kapibara.model.account;

import java.time.LocalDate;
import java.time.LocalDateTime;
public record AccountRow(long id, String fullName, String username,
                         AccountRole role, AccountStatus status,
                         LocalDateTime lastLogin, LocalDate createdDate) {}