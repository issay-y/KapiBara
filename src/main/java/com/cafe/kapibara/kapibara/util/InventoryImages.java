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

    /** Copies the picked file into the app folder or XAMPP category folder and returns the stored file name/path. */
    public static String save(Path source) throws IOException {
        return save(source, null);
    }

    public static String save(Path source, String category) throws IOException {
        Path root = com.cafe.kapibara.kapibara.DAO.InventoryDAO.imageRoot();
        if (Files.isDirectory(root)) {
            return com.cafe.kapibara.kapibara.DAO.InventoryDAO.saveImage(source.toFile(), category);
        }
        Files.createDirectories(DIR);
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        String stored = UUID.randomUUID() + name.substring(name.lastIndexOf('.'));
        Files.copy(source, DIR.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        return stored;
    }

    public static Optional<Path> find(String stored) {
        if (stored == null || stored.isBlank()) return Optional.empty();
        java.io.File xamppFile = com.cafe.kapibara.kapibara.DAO.InventoryDAO.imageFile(stored);
        if (xamppFile != null && xamppFile.isFile()) return Optional.of(xamppFile.toPath());
        Path path = DIR.resolve(stored).normalize();
        return path.startsWith(DIR) && Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    public static void delete(String stored) {
        if (stored == null || stored.isBlank()) return;
        com.cafe.kapibara.kapibara.DAO.InventoryDAO.deleteImage(stored);
        Path path = DIR.resolve(stored).normalize();
        if (path.startsWith(DIR)) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {}
        }
    }
}