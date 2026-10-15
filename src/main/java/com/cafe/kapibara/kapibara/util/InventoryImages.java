package com.cafe.kapibara.kapibara.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Stores item photos as files under ~/.kapibara/images/inventory; the database keeps only the file name. */
public final class InventoryImages {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Path DIR =
            Path.of(System.getProperty("user.home"), ".kapibara", "images", "inventory");

    private InventoryImages() { }

    public static boolean isSupported(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    /** Copies the picked file into the app folder and returns the stored file name. */
    public static String save(Path source) throws IOException {
        Files.createDirectories(DIR);
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        String stored = UUID.randomUUID() + name.substring(name.lastIndexOf('.'));
        Files.copy(source, DIR.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        return stored;
    }

    public static Optional<Path> find(String stored) {
        if (stored == null || stored.isBlank()) return Optional.empty();
        Path path = DIR.resolve(stored).normalize();
        return path.startsWith(DIR) && Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    public static void delete(String stored) {
        find(stored).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // a leftover file is harmless
            }
        });
    }
}