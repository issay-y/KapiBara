package com.cafe.kapibara.kapibara.controller.main.cashier;

import java.time.LocalTime;
import java.util.Locale;

/**
 * What a cashier page controller can ask of the cashier shell (sidebar + top bar).
 * A page controller implements {@link Aware} to receive the shell after its FXML is loaded.
 */
public interface CashierShell {

    void navigate(CashierPage page);

    String getCurrentUserName();

    String getCurrentUserRole();

    /** Asks before signing out, then returns to the login window. */
    void logout();

    interface Aware {
        void attach(CashierShell shell);
    }

    /** "Good morning" / "Good afternoon" / "Good evening". */
    static String greeting() {
        int hour = LocalTime.now().getHour();
        return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
    }

    /** "Mika Santos" -> "Mika", "mika.santos" -> "Mika". */
    static String firstName(String name) {
        if (name == null || name.isBlank()) return "there";
        String first = name.trim().split("[\\s._-]+")[0];
        if (first.isEmpty()) return "there";
        return first.substring(0, 1).toUpperCase(Locale.ENGLISH) + first.substring(1);
    }
}