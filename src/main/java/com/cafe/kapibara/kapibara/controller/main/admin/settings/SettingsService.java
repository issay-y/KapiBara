package com.cafe.kapibara.kapibara.controller.main.admin.settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.prefs.Preferences;

public final class SettingsService {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SettingsService.class);

    private SettingsService() {}

    // ---------- Receipt ----------
    public static String getPhone()   { return PREFS.get("receipt.phone", ""); }
    public static String getEmail()   { return PREFS.get("receipt.email", ""); }
    public static String getAddress() { return PREFS.get("receipt.address", ""); }

    public static void saveReceipt(String phone, String email, String address) {
        PREFS.put("receipt.phone", phone);
        PREFS.put("receipt.email", email);
        PREFS.put("receipt.address", address);
    }

    // ---------- Void PIN ----------
    public static boolean isVoidPinRequired() { return PREFS.getBoolean("void.pin.required", false); }
    public static void setVoidPinRequired(boolean on) { PREFS.putBoolean("void.pin.required", on); }
    public static boolean hasVoidPin() { return !PREFS.get("void.pin.hash", "").isEmpty(); }

    public static void saveVoidPin(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PREFS.put("void.pin.salt", Base64.getEncoder().encodeToString(salt));
        PREFS.put("void.pin.hash", hash(pin, salt));
    }

    public static boolean verifyVoidPin(String pin) {
        if (!isVoidPinRequired()) return true;
        String saltB64 = PREFS.get("void.pin.salt", "");
        String stored = PREFS.get("void.pin.hash", "");
        if (saltB64.isEmpty() || stored.isEmpty()) return false;
        return stored.equals(hash(pin, Base64.getDecoder().decode(saltB64)));
    }

    private static String hash(String pin, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return Base64.getEncoder().encodeToString(md.digest(pin.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}