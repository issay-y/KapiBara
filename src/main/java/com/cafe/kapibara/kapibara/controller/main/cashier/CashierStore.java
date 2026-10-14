package com.cafe.kapibara.kapibara.controller.main.cashier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


public final class CashierStore {

    public static final String SHIFT_NAME = "Morning";
    public static final String SHIFT_HOURS = "8:00 AM – 4:00 PM";
    public static final String COUNTER = "Counter 01";
    public static final String BRANCH_NAME = "Caloocan Branch";
    public static final String BRANCH_SHORT = "Caloocan";
    public static final String BRANCH_ADDRESS = "Caloocan City";

    public static final List<String> CATEGORIES =
            List.of("Coffee", "Non-Coffee", "Pastries", "Snacks", "Add-ons");

    public static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.20");

    private static final BigDecimal VAT_DIVISOR = new BigDecimal("1.12");
    private static final CashierStore INSTANCE = new CashierStore();

    public static CashierStore get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------

    public static final class Product {
        private final String id;
        private final String name;
        private final String description;
        private final String category;
        private final BigDecimal price;
        private int stock;

        private Product(String id, String name, String description, String category,
                        String price, int stock) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.price = new BigDecimal(price).setScale(2);
            this.stock = stock;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public BigDecimal getPrice() { return price; }
        public int getStock() { return stock; }
        public boolean isAvailable() { return stock > 0; }
    }

    public static final class Line {
        private final Product product;
        private int qty;

        private Line(Product product) { this.product = product; }

        public Product getProduct() { return product; }
        public int getQty() { return qty; }
        public BigDecimal lineTotal() { return product.price.multiply(BigDecimal.valueOf(qty)); }
    }

    /** One row of a finished sale (a snapshot, so the receipt never changes). */
    public record SaleLine(String name, BigDecimal unitPrice, int qty) {
        public BigDecimal amount() { return unitPrice.multiply(BigDecimal.valueOf(qty)); }
    }

    public record Sale(String number, LocalDateTime time, String cashier, int items,
                       List<SaleLine> lines, BigDecimal subtotal, BigDecimal vat,
                       BigDecimal discount, boolean seniorPwd, BigDecimal total,
                       BigDecimal cashReceived, BigDecimal change) { }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private final List<Product> products = new ArrayList<>();
    private final List<Sale> sales = new ArrayList<>();
    private final Map<Product, Line> cart = new LinkedHashMap<>();
    private int nextSeq = 1;
    private LocalDate seqDay = LocalDate.now();
    private LocalDateTime orderStarted;
    private boolean seniorPwd;
    private Sale lastSale;

    private Sale viewedSale;
    private CashierPage receiptReturn;

    private CashierStore() {
        seed();
    }

    private void seed() {
        add("latte", "Cafe Latte", "Bold brew, perfectly chilled", "Coffee", "95", 32);
        add("spanish-latte", "Spanish Latte", "Espresso & sweet creamy milk", "Coffee", "125", 24);
        add("caramel-macchiato", "Caramel Macchiato", "Caramel drizzle & espresso", "Coffee", "145", 18);
        add("americano", "Café Americano", "Smooth, full-bodied espresso", "Coffee", "95", 30);
        add("cappuccino", "Cappuccino", "Velvety foam, rich espresso", "Coffee", "120", 0);
        add("mocha", "Mocha", "Chocolate meets espresso", "Coffee", "135", 20);
        add("flat-white", "Flat White", "Silky microfoam, double shot", "Coffee", "130", 22);

        add("matcha-latte", "Matcha Latte", "Earthy matcha & milk", "Non-Coffee", "135", 16);
        add("chocolate", "Chocolate", "Rich cocoa comfort", "Non-Coffee", "110", 20);
        add("milk-tea", "Milk Tea", "Black tea & creamy milk", "Non-Coffee", "110", 15);

        add("croissant", "Croissant", "Buttery, flaky layers", "Pastries", "85", 12);
        add("muffin", "Muffin", "Freshly baked blueberry", "Pastries", "70", 8);
        add("cookies", "Cookies", "Chocolate chip classic", "Pastries", "55", 18);

        add("sandwich", "Sandwich", "Ham, cheese & greens", "Snacks", "125", 10);
        add("french-fries", "French Fries", "Crisp, golden & salted", "Snacks", "65", 14);

        add("extra-espresso", "Extra Espresso", "One extra espresso shot", "Add-ons", "30", 30);
        add("whipped-cream", "Whipped Cream", "A fluffy finishing touch", "Add-ons", "20", 24);
        add("caramel-syrup", "Caramel Syrup", "Sweet caramel boost", "Add-ons", "20", 0);
    }

    private void add(String id, String name, String desc, String category, String price, int stock) {
        products.add(new Product(id, name, desc, category, price, stock));
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    public List<Product> products() {
        return Collections.unmodifiableList(products);
    }


    public List<Product> search(String category, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return products.stream()
                .filter(p -> q.isEmpty()
                        ? (category == null || category.equals(p.category))
                        : (p.name.toLowerCase(Locale.ROOT).contains(q)
                        || p.description.toLowerCase(Locale.ROOT).contains(q)
                        || p.category.toLowerCase(Locale.ROOT).contains(q)))
                .toList();
    }

    public Optional<Product> firstOutOfStock() {
        return products.stream().filter(p -> !p.isAvailable()).findFirst();
    }

    public long outOfStockCount() {
        return products.stream().filter(p -> !p.isAvailable()).count();
    }

    // ------------------------------------------------------------------
    // Current order
    // ------------------------------------------------------------------

    public String currentOrderNumber() {
        if (cart.isEmpty()) {
            LocalDate today = LocalDate.now();
            if (!today.equals(seqDay)) {
                seqDay = today;
                nextSeq = 1;
            }
        }
        return "KB-" + seqDay.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%03d", nextSeq);
    }

    public LocalDateTime orderTime() {
        return (cart.isEmpty() || orderStarted == null) ? LocalDateTime.now() : orderStarted;
    }

    public int qtyInCart(Product p) {
        Line line = cart.get(p);
        return line == null ? 0 : line.qty;
    }

    public boolean add(Product p) {
        if (qtyInCart(p) + 1 > p.stock) return false;
        if (cart.isEmpty()) orderStarted = LocalDateTime.now();
        cart.computeIfAbsent(p, Line::new).qty++;
        return true;
    }

    public void decrease(Product p) {
        Line line = cart.get(p);
        if (line != null && line.qty > 1) line.qty--;
    }

    public void remove(Product p) {
        cart.remove(p);
        if (cart.isEmpty()) seniorPwd = false;
    }

    public void clear() {
        cart.clear();
        seniorPwd = false;
    }

    public List<Line> lines() {
        return new ArrayList<>(cart.values());
    }

    public boolean isEmpty() {
        return cart.isEmpty();
    }

    public int itemCount() {
        return cart.values().stream().mapToInt(l -> l.qty).sum();
    }

    // Money

    private BigDecimal gross() {
        return cart.values().stream().map(Line::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isSeniorPwd() {
        return seniorPwd;
    }

    public void setSeniorPwd(boolean value) {
        this.seniorPwd = value && !cart.isEmpty();
    }

    public BigDecimal subtotal() {
        return gross().divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal vat() {
        return seniorPwd ? BigDecimal.ZERO.setScale(2) : gross().subtract(subtotal());
    }

    public BigDecimal discount() {
        return seniorPwd
                ? subtotal().multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal total() {
        return seniorPwd ? subtotal().subtract(discount()) : gross();
    }

    public Sale checkout(BigDecimal cashReceived, String cashier) {
        if (cart.isEmpty()) throw new IllegalStateException("The order is empty.");
        BigDecimal total = total();
        if (cashReceived == null || cashReceived.compareTo(total) < 0) {
            throw new IllegalStateException("The cash received is less than the total.");
        }
        for (Line l : cart.values()) {
            if (l.qty > l.product.stock) {
                throw new IllegalStateException(l.product.name + " only has " + l.product.stock + " left.");
            }
        }

        String number = currentOrderNumber();
        int items = itemCount();
        List<SaleLine> lines = cart.values().stream()
                .map(l -> new SaleLine(l.product.name, l.product.price, l.qty))
                .toList();
        BigDecimal subtotal = subtotal();
        BigDecimal vat = vat();
        BigDecimal discount = discount();
        boolean pwd = seniorPwd;

        cart.values().forEach(l -> l.product.stock -= l.qty);

        Sale sale = new Sale(number, LocalDateTime.now(), cashier, items, lines, subtotal, vat,
                discount, pwd, total, cashReceived, cashReceived.subtract(total));
        sales.add(sale);
        lastSale = sale;
        viewedSale = sale;
        receiptReturn = null;
        cart.clear();
        seniorPwd = false;
        nextSeq++;
        return sale;
    }

    // ------------------------------------------------------------------
    // Shift totals
    // ------------------------------------------------------------------

    public Optional<Sale> lastSale() {
        return Optional.ofNullable(lastSale);
    }

    public int salesCount() {
        return sales.size();
    }

    public BigDecimal shiftSales() {
        return sales.stream().map(Sale::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal averageSale() {
        if (sales.isEmpty()) return BigDecimal.ZERO.setScale(2);
        return shiftSales().divide(BigDecimal.valueOf(sales.size()), 2, RoundingMode.HALF_UP);
    }

    public List<Sale> recentSales(int limit) {
        List<Sale> copy = new ArrayList<>(sales);
        Collections.reverse(copy);
        return copy.size() > limit ? copy.subList(0, limit) : copy;
    }

    public Optional<LocalDateTime> latestSaleTime() {
        return sales.isEmpty() ? Optional.empty() : Optional.of(sales.get(sales.size() - 1).time());
    }

    public List<Sale> sales() {
        return Collections.unmodifiableList(sales);
    }

    public BigDecimal todaySalesTotal() {
        LocalDate today = LocalDate.now();
        return sales.stream()
                .filter(s -> s.time().toLocalDate().equals(today))
                .map(Sale::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Sale being viewed (transaction detail / receipt)
    // ------------------------------------------------------------------

    public Optional<Sale> viewedSale() {
        return Optional.ofNullable(viewedSale);
    }

    public void setViewedSale(Sale sale) {
        this.viewedSale = sale;
    }

    public Optional<CashierPage> receiptReturn() {
        return Optional.ofNullable(receiptReturn);
    }

    public void setReceiptReturn(CashierPage page) {
        this.receiptReturn = page;
    }

    // ------------------------------------------------------------------
    // Money text
    // ------------------------------------------------------------------

    public static String money(BigDecimal v) {
        return String.format(Locale.US, "₱%,.2f", v);
    }

    public static String moneyShort(BigDecimal v) {
        return v.stripTrailingZeros().scale() <= 0
                ? String.format(Locale.US, "₱%,.0f", v)
                : money(v);
    }

    public static BigDecimal parseMoney(String text) {
        if (text == null) return null;
        String clean = text.replace("₱", "").replace(",", "").trim();
        if (clean.isEmpty()) return null;
        try {
            BigDecimal value = new BigDecimal(clean);
            return value.signum() < 0 ? null : value.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}