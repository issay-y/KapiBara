package com.cafe.kapibara.kapibara.controller.main.cashier;

import com.cafe.kapibara.kapibara.DAO.InventoryDAO;
import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import com.cafe.kapibara.kapibara.util.InventoryImages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Builds the menu cards and category chips used on the New Order and Browse screens. */
public final class ProductCards {

    /** Fallback folder for relative image names that are not inventory photos. */
    private static final String IMG_DIR = "/com/cafe/kapibara/kapibara/images/menu/";
    private static final Map<String, Optional<Image>> IMAGES = new HashMap<>();

    private static final double PHOTO_COMPACT = 128;   // New Order (3 columns)
    private static final double PHOTO_WIDE = 104;      // Browse (4 columns)
    private static final double RIM = 5;               // cream ring around the photo

    private ProductCards() { }

    /** Call when a page opens so edited pictures show up. */
    public static void clearImageCache() {
        IMAGES.clear();
    }

    // ------------------------------------------------------------------
    // Small helpers (also used by the page controllers)
    // ------------------------------------------------------------------

    public static Label label(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        return l;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Button chip(String text, boolean active, Runnable onClick) {
        Button b = new Button(text);
        b.getStyleClass().add("csh-chip");
        if (active) b.getStyleClass().add("csh-chip-active");
        b.setFocusTraversable(false);
        b.setOnAction(e -> onClick.run());
        return b;
    }

    // ------------------------------------------------------------------
    // Cards: round photo on the left, details on the right
    // (name, category, price, quantity, availability, Add button)
    // ------------------------------------------------------------------

    /** Used by the New Order grid (3 columns). */
    public static VBox compact(Product p, int inCart, Consumer<Product> onAdd) {
        return build(p, inCart, onAdd, PHOTO_COMPACT);
    }

    /** Used by Browse (4 columns): same card with a slightly smaller photo. */
    public static VBox wide(Product p, int inCart, Consumer<Product> onAdd) {
        return build(p, inCart, onAdd, PHOTO_WIDE);
    }

    private static VBox build(Product p, int inCart, Consumer<Product> onAdd, double photoSize) {
        Button action = actionButton(p, inCart, onAdd);
        VBox.setMargin(action, new Insets(6, 0, 0, 0));

        VBox info = new VBox(3, name(p), category(p), priceRow(p), qty(p), status(p), action);
        info.setAlignment(Pos.CENTER_LEFT);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox row = new HBox(14, photo(p, photoSize), info);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = baseCard(inCart);
        card.getChildren().add(row);
        return card;
    }

    private static VBox baseCard(int inCart) {
        VBox card = new VBox();
        card.getStyleClass().add("csh-product-card");
        if (inCart > 0) card.getStyleClass().add("csh-product-card-selected");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(0);
        card.setMinHeight(Region.USE_PREF_SIZE);   // never squeezed shorter than its content
        return card;
    }

    /** One line; a long name ends in "..." and shows in full when hovered. */
    private static Label name(Product p) {
        Label l = label(p.getName(), "csh-product-name");
        l.setMaxWidth(Double.MAX_VALUE);
        Tooltip.install(l, new Tooltip(p.getName()));
        return l;
    }

    private static Label category(Product p) {
        Label l = label(p.getCategory(), "csh-product-category");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static HBox priceRow(Product p) {
        HBox row = new HBox(6, label(CashierStore.money(p.getPrice()), "csh-product-price"),
                label("/ " + unit(p), "csh-product-unit"));
        row.setAlignment(Pos.BOTTOM_LEFT);
        return row;
    }

    private static Label qty(Product p) {
        return label("Qty: " + p.getStock() + " " + unit(p), "csh-product-qty");
    }

    private static HBox status(Product p) {
        String text = !p.isAvailable() ? "Out of stock" : p.isLow() ? "Low stock" : "Available";
        String style = !p.isAvailable() ? "csh-tag-out" : p.isLow() ? "csh-tag-low" : "csh-tag-ok";
        Label pill = label(text, "csh-tag");
        pill.getStyleClass().add(style);
        HBox box = new HBox(pill);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static String unit(Product p) {
        String u = p.getUnit();
        return u == null || u.isBlank() ? "unit" : u;
    }

    private static Button actionButton(Product p, int inCart, Consumer<Product> onAdd) {
        Button b;
        if (!p.isAvailable()) {
            b = new Button("＋   Unavailable");
            b.getStyleClass().add("csh-unavail-btn");
            b.setDisable(true);
        } else if (inCart > 0) {
            b = new Button("✓   Added · " + inCart);
            b.getStyleClass().add("csh-added-btn");
        } else {
            b = new Button("＋   Add");
            b.getStyleClass().add("csh-add-btn");
        }
        b.setMaxWidth(Double.MAX_VALUE);
        b.setFocusTraversable(false);
        if (p.isAvailable()) b.setOnAction(e -> onAdd.accept(p));
        return b;
    }

    // ------------------------------------------------------------------
    // Photo: a circle with a cream ring and soft shadow. Photos live in
    // htdocs/Kapibara/<Category>/ and are found through InventoryDAO.imageFile
    // (image_path holds "Category/File.png"). The picture is cropped to fill
    // the circle. Items without a photo show the first letter of their category.
    // ------------------------------------------------------------------

    private static StackPane photo(Product p, double size) {
        double inner = size - RIM * 2;

        StackPane disc = new StackPane();
        disc.setMinSize(inner, inner);
        disc.setPrefSize(inner, inner);
        disc.setMaxSize(inner, inner);
        disc.setClip(new Circle(inner / 2, inner / 2, inner / 2));
        disc.setOpacity(p.isAvailable() ? 1.0 : 0.5);

        String key = p.getImagePath() == null ? "" : p.getImagePath();
        Image image = IMAGES.computeIfAbsent(key, ProductCards::load).orElse(null);
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setManaged(false);   // sized here, never by the layout
            view.setSmooth(true);
            cropToFill(view, image, inner, inner);
            disc.getChildren().add(view);
        } else {
            disc.getChildren().add(label(monogram(p.getCategory()), "csh-product-monogram"));
        }

        StackPane ring = new StackPane(disc);
        ring.getStyleClass().add("csh-photo-ring");
        ring.setMinSize(size, size);
        ring.setPrefSize(size, size);
        ring.setMaxSize(size, size);
        return ring;
    }

    /** "Cover" fit: scale to fill and crop the overflow from the centre, no stretching. */
    private static void cropToFill(ImageView view, Image image, double w, double h) {
        double iw = image.getWidth(), ih = image.getHeight();
        if (iw <= 0 || ih <= 0) return;

        double target = w / h;
        double vw, vh;
        if (iw / ih > target) {
            vh = ih;
            vw = ih * target;
        } else {
            vw = iw;
            vh = iw / target;
        }
        view.setViewport(new Rectangle2D((iw - vw) / 2, (ih - vh) / 2, vw, vh));
        view.setPreserveRatio(false);
        view.setFitWidth(w);
        view.setFitHeight(h);
        view.relocate(0, 0);
    }

    /** First letter of the category name, so any category added later gets a symbol automatically. */
    private static String monogram(String category) {
        if (category == null || category.isBlank()) return "?";
        return category.trim().substring(0, 1).toUpperCase();
    }

    private static Optional<Image> load(String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        try {
            // 1. the photo in htdocs/Kapibara/<Category>/
            File stored = InventoryDAO.imageFile(path);
            if (stored != null) {
                Optional<Image> img = valid(new Image(stored.toURI().toString(), 400, 0, true, true));
                if (img.isEmpty()) System.err.println("Image could not be decoded: " + stored);
                return img;
            }
            Optional<java.nio.file.Path> userImg = InventoryImages.find(path);
            if (userImg.isPresent()) {
                return valid(new Image(userImg.get().toUri().toString(), 400, 0, true, true));
            }
            // 2. a full file path or file: URL
            File file = new File(path);
            if (file.isFile()) {
                return valid(new Image(file.toURI().toString(), 400, 0, true, true));
            }
            if (path.startsWith("file:")) {
                return valid(new Image(path, 400, 0, true, true));
            }
            // 3. a picture bundled with the app
            String resource = path.startsWith("/") ? path : IMG_DIR + path;
            try (InputStream in = ProductCards.class.getResourceAsStream(resource)) {
                if (in != null) return valid(new Image(in));
            }
            System.err.println("Image not found: \"" + path + "\" (looked in " + InventoryDAO.imageRoot() + ")");
        } catch (Exception ex) {
            System.err.println("Image load failed for \"" + path + "\": " + ex.getMessage());
        }
        return Optional.empty();
    }

    private static Optional<Image> valid(Image image) {
        return image.isError() ? Optional.empty() : Optional.of(image);
    }
}