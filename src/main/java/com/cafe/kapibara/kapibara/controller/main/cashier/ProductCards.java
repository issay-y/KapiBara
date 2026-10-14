package com.cafe.kapibara.kapibara.controller.main.cashier;

import com.cafe.kapibara.kapibara.controller.main.cashier.CashierStore.Product;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Builds the menu cards and category chips used on the New Order and Browse screens. */
public final class ProductCards {

    private static final String IMG_DIR = "/com/cafe/kapibara/kapibara/images/menu/";
    private static final Map<String, Optional<Image>> IMAGES = new HashMap<>();

    private ProductCards() { }

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
    // Cards
    // ------------------------------------------------------------------

    /** Vertical card: picture on top. Used by the New Order grid (3 columns). */
    public static VBox compact(Product p, int inCart, Consumer<Product> onAdd) {
        VBox card = baseCard(inCart);

        HBox pictureRow = new HBox(imageTile(p, 96));
        pictureRow.setAlignment(Pos.CENTER);

        Label desc = label(p.getDescription(), "csh-product-desc");
        desc.setWrapText(true);

        card.getChildren().addAll(pictureRow, label(p.getName(), "csh-product-name"), desc,
                grow(), priceRow(p), actionButton(p, inCart, onAdd));
        return card;
    }

    /** Card with the category on top and the picture on the right. Used by Browse (4 columns). */
    public static VBox wide(Product p, int inCart, Consumer<Product> onAdd) {
        VBox card = baseCard(inCart);

        HBox category = new HBox(6, label(icon(p.getCategory()), "csh-product-cat-icon"),
                label(p.getCategory(), "csh-product-cat"));
        category.setAlignment(Pos.CENTER_LEFT);

        Label desc = label(p.getDescription(), "csh-product-desc");
        desc.setWrapText(true);

        VBox text = new VBox(4, category, label(p.getName(), "csh-product-name"), desc);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox top = new HBox(10, text, imageTile(p, 84));
        top.setAlignment(Pos.TOP_LEFT);

        card.getChildren().addAll(top, grow(), priceRow(p), actionButton(p, inCart, onAdd));
        return card;
    }

    private static VBox baseCard(int inCart) {
        VBox card = new VBox(8);
        card.getStyleClass().add("csh-product-card");
        if (inCart > 0) card.getStyleClass().add("csh-product-card-selected");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /** Pushes the price row and button to the bottom so buttons line up across a row of cards. */
    private static Region grow() {
        Region r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    private static HBox priceRow(Product p) {
        Label avail = p.isAvailable()
                ? label(p.getStock() + " available", "csh-avail")
                : label("Out of stock", "csh-avail-out");
        HBox row = new HBox(label(CashierStore.moneyShort(p.getPrice()), "csh-product-price"),
                spacer(), avail);
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
    // Picture: resources/.../images/menu/{product id}.png, or a category symbol when missing
    // ------------------------------------------------------------------

    private static StackPane imageTile(Product p, double size) {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("csh-product-img");
        tile.setMinSize(size, size);
        tile.setPrefSize(size, size);
        tile.setMaxSize(size, size);

        Image image = IMAGES.computeIfAbsent(p.getId(), ProductCards::load).orElse(null);
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(size - 12);
            view.setFitHeight(size - 12);
            view.setPreserveRatio(true);
            tile.getChildren().add(view);
        } else {
            tile.getChildren().add(label(icon(p.getCategory()), "csh-product-emoji"));
        }
        return tile;
    }

    private static Optional<Image> load(String id) {
        try (InputStream in = ProductCards.class.getResourceAsStream(IMG_DIR + id + ".png")) {
            return in == null ? Optional.empty() : Optional.of(new Image(in));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String icon(String category) {
        return switch (category) {
            case "Coffee" -> "☕";
            case "Non-Coffee" -> "🥤";
            case "Pastries" -> "🥐";
            case "Snacks" -> "🥪";
            default -> "＋";
        };
    }
}