package com.cafe.kapibara.kapibara.controller.main.cashier;

import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import com.cafe.kapibara.kapibara.util.InventoryImages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Builds the menu cards and category chips used on the New Order and Browse screens. */
public final class ProductCards {

    /** Fallback folder for relative image names that are not inventory photos. */
    private static final String IMG_DIR = "/com/cafe/kapibara/kapibara/images/menu/";
    private static final Map<String, Optional<Image>> IMAGES = new HashMap<>();

    private static final double COVER_COMPACT = 200;
    private static final double COVER_WIDE = 170;

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
    // Cards: tall rectangle. Picture with tags on top, then name, description,
    // details (availability, unit, in this order), price and the Add button.
    // ------------------------------------------------------------------

    /** Used by the New Order grid (3 columns). */
    public static VBox compact(Product p, int inCart, Consumer<Product> onAdd) {
        return build(p, inCart, onAdd, COVER_COMPACT);
    }

    /** Used by Browse (4 columns): same card with a slightly shorter picture. */
    public static VBox wide(Product p, int inCart, Consumer<Product> onAdd) {
        return build(p, inCart, onAdd, COVER_WIDE);
    }

    private static VBox build(Product p, int inCart, Consumer<Product> onAdd, double coverHeight) {
        VBox card = baseCard(inCart);
        card.getChildren().addAll(
                cover(p, coverHeight),
                name(p),
                category(p),
                divider(),
                details(p, inCart),
                grow(),
                priceRow(p),
                actionButton(p, inCart, onAdd));
        return card;
    }

    private static VBox baseCard(int inCart) {
        VBox card = new VBox(10);
        card.getStyleClass().add("csh-product-card");
        if (inCart > 0) card.getStyleClass().add("csh-product-card-selected");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(0);
        card.setMinHeight(Region.USE_PREF_SIZE);   // never squeezed shorter than its content
        return card;
    }

    private static Label name(Product p) {
        Label l = label(p.getName(), "csh-product-name");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    /** The item's category, shown under the name. */
    private static Label category(Product p) {
        Label l = label(p.getCategory(), "csh-product-category");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static Region divider() {
        Region line = new Region();
        line.getStyleClass().add("csh-product-divider");
        line.setMinHeight(1);
        line.setPrefHeight(1);
        line.setMaxHeight(1);
        return line;
    }

    private static VBox details(Product p, int inCart) {
        VBox box = new VBox(6);

        String stockText;
        String stockStyle;
        if (!p.isAvailable()) {
            stockText = "Out of stock";
            stockStyle = "csh-detail-out";
        } else if (p.isLow()) {
            stockText = p.getStock() + " " + unit(p) + " left · low stock";
            stockStyle = "csh-detail-low";
        } else {
            stockText = p.getStock() + " " + unit(p) + " available";
            stockStyle = "csh-detail-ok";
        }
        box.getChildren().add(detailRow("Availability", stockText, stockStyle));
        box.getChildren().add(detailRow("Sold per", unit(p), null));

        if (inCart > 0) {
            BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(inCart));
            box.getChildren().add(detailRow("In this order",
                    inCart + " × " + CashierStore.moneyShort(p.getPrice())
                            + " = " + CashierStore.moneyShort(lineTotal), "csh-detail-ok"));
        }
        return box;
    }

    private static HBox detailRow(String key, String value, String valueStyle) {
        Label val = label(value, "csh-detail-val");
        if (valueStyle != null) val.getStyleClass().add(valueStyle);
        HBox row = new HBox(label(key, "csh-detail-key"), spacer(), val);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static String unit(Product p) {
        String u = p.getUnit();
        return u == null || u.isBlank() ? "unit" : u;
    }

    /** Pushes the price row and button to the bottom so buttons line up across a row of cards. */
    private static Region grow() {
        Region r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    private static HBox priceRow(Product p) {
        HBox row = new HBox(6, label(CashierStore.moneyShort(p.getPrice()), "csh-product-price"),
                label("per " + unit(p), "csh-product-unit"));
        row.setAlignment(Pos.BOTTOM_LEFT);
        return row;
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
    // Picture. Photos saved from the inventory form are found through InventoryImages
    // (image_path holds only the file name). The picture is cropped to fill the whole
    // banner. Items without a photo show a category symbol instead.
    // ------------------------------------------------------------------

    private static StackPane cover(Product p, double height) {
        StackPane cover = new StackPane();
        cover.getStyleClass().add("csh-product-img");
        cover.setMinSize(0, height);
        cover.setPrefHeight(height);
        cover.setMaxSize(Double.MAX_VALUE, height);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(cover.widthProperty());
        clip.heightProperty().bind(cover.heightProperty());
        cover.setClip(clip);

        String key = p.getImagePath() == null ? "" : p.getImagePath();
        Image image = IMAGES.computeIfAbsent(key, ProductCards::load).orElse(null);
        double fade = p.isAvailable() ? 1.0 : 0.5;

        if (image != null) {
            ImageView view = new ImageView(image);
            view.setManaged(false);   // sized by the banner, never the other way round
            view.setSmooth(true);
            view.setOpacity(fade);
            cover.widthProperty().addListener((o, a, b) -> cropToBanner(view, image, cover));
            cover.heightProperty().addListener((o, a, b) -> cropToBanner(view, image, cover));
            cover.getChildren().add(view);
            cropToBanner(view, image, cover);
        } else {
            VBox placeholder = new VBox(4, label(monogram(p.getCategory()), "csh-product-monogram"),
                    label("No photo yet", "csh-product-placeholder-text"));
            placeholder.setAlignment(Pos.CENTER);
            placeholder.setMouseTransparent(true);
            placeholder.setOpacity(fade);
            cover.getChildren().add(placeholder);
        }

        // tags sit on top of the picture
        Label status = label(statusText(p), "csh-tag");
        status.getStyleClass().add(statusStyle(p));
        StackPane.setAlignment(status, Pos.TOP_RIGHT);
        StackPane.setMargin(status, new Insets(10));

        cover.getChildren().add(status);
        return cover;
    }

    private static String statusText(Product p) {
        return !p.isAvailable() ? "Out of stock" : p.isLow() ? "Low stock" : "In stock";
    }

    private static String statusStyle(Product p) {
        return !p.isAvailable() ? "csh-tag-out" : p.isLow() ? "csh-tag-low" : "csh-tag-ok";
    }

    /** "Cover" fit: scale to fill the banner and crop the overflow from the centre, no stretching. */
    private static void cropToBanner(ImageView view, Image image, StackPane banner) {
        double w = banner.getWidth(), h = banner.getHeight();
        double iw = image.getWidth(), ih = image.getHeight();
        if (w <= 0 || h <= 0 || iw <= 0 || ih <= 0) return;

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

    private static Optional<Image> load(String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        try {
            // 1. the photo saved by the inventory form
            Optional<Path> stored = InventoryImages.find(path);
            if (stored.isPresent()) {
                return valid(new Image(stored.get().toUri().toString(), 720, 0, true, true));
            }
            // 2. a full file path or file: URL
            File file = new File(path);
            if (file.isFile()) {
                return valid(new Image(file.toURI().toString(), 720, 0, true, true));
            }
            if (path.startsWith("file:")) {
                return valid(new Image(path, 720, 0, true, true));
            }
            // 3. a picture bundled with the app
            String resource = path.startsWith("/") ? path : IMG_DIR + path;
            try (InputStream in = ProductCards.class.getResourceAsStream(resource)) {
                if (in != null) return valid(new Image(in));
            }
        } catch (Exception ignored) {
            // fall through to the category symbol
        }
        return Optional.empty();
    }

    private static Optional<Image> valid(Image image) {
        return image.isError() ? Optional.empty() : Optional.of(image);
    }

    /** First letter of the category name, so any category added later gets a symbol automatically. */
    private static String monogram(String category) {
        if (category == null || category.isBlank()) return "?";
        return category.trim().substring(0, 1).toUpperCase();
    }
}