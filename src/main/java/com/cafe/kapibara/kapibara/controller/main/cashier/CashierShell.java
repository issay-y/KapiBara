package com.cafe.kapibara.kapibara.controller.main.cashier;

import java.time.LocalTime;
import java.util.Locale;


public interface CashierShell {

    void navigate(CashierPage page);
    int getCurrentUserId();
    String getCurrentUserName();
    String getCurrentUserRole();

    void logout();

    interface Aware {
        void attach(CashierShell shell);
    }

    static String greeting() {
        int hour = LocalTime.now().getHour();
        return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
    }

    static String firstName(String name) {
        if (name == null || name.isBlank()) return "there";
        String first = name.trim().split("[\\s._-]+")[0];
        if (first.isEmpty()) return "there";
        return first.substring(0, 1).toUpperCase(Locale.ENGLISH) + first.substring(1);
    }


}