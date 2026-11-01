package com.thinkmart.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StoreService {
    private static final long FREE_SHIPPING_MINIMUM = 5000;
    private static final long SHIPPING_FEE = 99;
    private static final int PASSWORD_ITERATIONS = 210_000;
    private static final int MAX_QUANTITY = 99;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    public StoreService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Product(String id, String name, String category, long price, double rating,
                          String tag, String description, String image) {}

    public record Customer(long id, String name, String email) {}

    public record OrderItemRequest(String id, int quantity) {}

    public record CheckoutRequest(String name, String email, String phone, String address,
                                  String city, String postalCode, List<OrderItemRequest> items) {}

    public record OrderItem(String id, String name, long price, int quantity) {}

    public record Order(String id, String placedAt, String name, String email, String phone,
                        String address, String city, String postalCode, List<OrderItem> items,
                        long subtotal, long shipping, long total, String payment) {}

    public List<Product> products() {
        return jdbc.query("""
                SELECT id, name, category, price, rating, tag, description, image
                FROM products ORDER BY CASE id
                    WHEN 'laptop' THEN 1 WHEN 'headphones' THEN 2 WHEN 'watch' THEN 3 WHEN 'phone' THEN 4
                    WHEN 'backpack' THEN 5 WHEN 'camera' THEN 6 WHEN 'lamp' THEN 7 WHEN 'speaker' THEN 8 ELSE 9 END, name
                """, (row, index) -> new Product(
                row.getString("id"), row.getString("name"), row.getString("category"),
                row.getLong("price"), row.getDouble("rating"), row.getString("tag"),
                row.getString("description"), row.getString("image")));
    }

    @Transactional
    public String register(String rawName, String rawEmail, String password) {
        String name = requiredText(rawName, "Name", 2, 80);
        String email = normalizedEmail(rawEmail);
        validatePassword(password);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE email = ?", Integer.class, email) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }

        byte[] salt = randomBytes(16);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO customers (name, email, password_salt, password_hash)
                VALUES (?, ?, ?, ?)
                """, new String[]{"id"});
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, HexFormat.of().formatHex(salt));
            statement.setString(4, passwordHash(password, salt));
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) throw new IllegalStateException("The account ID was not generated.");
        long customerId = generatedId.longValue();
        return createSession(customerId);
    }

    public String login(String rawEmail, String password) {
        String email = normalizedEmail(rawEmail);
        if (password == null || password.length() > 128) {
            throw unauthorized();
        }
        List<CustomerPassword> matches = jdbc.query("""
                SELECT id, name, email, password_salt, password_hash FROM customers WHERE email = ?
                """, (row, index) -> new CustomerPassword(
                new Customer(row.getLong("id"), row.getString("name"), row.getString("email")),
                row.getString("password_salt"), row.getString("password_hash")), email);
        if (matches.isEmpty()) {
            throw unauthorized();
        }
        CustomerPassword saved = matches.get(0);
        String candidate = passwordHash(password, HexFormat.of().parseHex(saved.salt()));
        if (!MessageDigest.isEqual(candidate.getBytes(StandardCharsets.US_ASCII),
                saved.hash().getBytes(StandardCharsets.US_ASCII))) {
            throw unauthorized();
        }
        return createSession(saved.customer().id());
    }

    public Customer currentCustomer(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) {
            throw unauthorized();
        }
        List<Customer> matches = jdbc.query("""
                SELECT c.id, c.name, c.email FROM auth_sessions s
                JOIN customers c ON c.id = s.customer_id
                WHERE s.token_hash = ? AND s.expires_at > ?
                """, (row, index) -> new Customer(row.getLong("id"), row.getString("name"), row.getString("email")),
                digest(rawToken), Timestamp.from(Instant.now()));
        if (matches.isEmpty()) {
            throw unauthorized();
        }
        return matches.get(0);
    }

    public Customer optionalCustomer(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        try {
            return currentCustomer(rawToken);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) return null;
            throw exception;
        }
    }

    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank() && rawToken.length() <= 128) {
            jdbc.update("DELETE FROM auth_sessions WHERE token_hash = ?", digest(rawToken));
        }
    }

    public List<Order> ordersFor(Customer customer) {
        List<OrderHeader> headers = jdbc.query("""
                SELECT id, customer_name, customer_email, customer_phone, delivery_address, delivery_city,
                       delivery_postal_code, subtotal, shipping, total, placed_at
                FROM orders WHERE customer_id = ? ORDER BY placed_at DESC
                """, (row, index) -> new OrderHeader(
                row.getString("id"), row.getString("customer_name"), row.getString("customer_email"),
                row.getString("customer_phone"), row.getString("delivery_address"), row.getString("delivery_city"),
                row.getString("delivery_postal_code"), row.getLong("subtotal"), row.getLong("shipping"),
                row.getLong("total"), row.getTimestamp("placed_at").toInstant().toString()), customer.id());
        return headers.stream().map(this::loadOrder).toList();
    }

    @Transactional
    public Order placeOrder(CheckoutRequest request, Customer customer) {
        if (request == null) throw badRequest("Order details are required.");
        String name = requiredText(request.name(), "Name", 2, 80);
        String email = normalizedEmail(request.email());
        String phone = requiredText(request.phone(), "Phone", 7, 20);
        String address = requiredText(request.address(), "Address", 4, 160);
        String city = requiredText(request.city(), "City", 2, 80);
        String postalCode = requiredText(request.postalCode(), "Postal code", 4, 12);
        if (request.items() == null || request.items().isEmpty() || request.items().size() > 50) {
            throw badRequest("Your cart must contain between 1 and 50 items.");
        }

        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            if (item == null || item.id() == null || item.id().length() > 40
                    || item.quantity() < 1 || item.quantity() > MAX_QUANTITY) {
                throw badRequest("One or more cart items are invalid.");
            }
            int combined = quantities.getOrDefault(item.id(), 0) + item.quantity();
            if (combined > MAX_QUANTITY) throw badRequest("The quantity for an item cannot exceed 99.");
            quantities.put(item.id(), combined);
        }

        List<OrderItem> items = new ArrayList<>();
        long subtotal = 0;
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            List<Product> products = jdbc.query("""
                    SELECT id, name, category, price, rating, tag, description, image
                    FROM products WHERE id = ?
                    """, (row, index) -> new Product(
                    row.getString("id"), row.getString("name"), row.getString("category"),
                    row.getLong("price"), row.getDouble("rating"), row.getString("tag"),
                    row.getString("description"), row.getString("image")), entry.getKey());
            if (products.isEmpty()) throw badRequest("An item in your cart is no longer available.");
            Product product = products.get(0);
            subtotal = Math.addExact(subtotal, Math.multiplyExact(product.price(), entry.getValue()));
            items.add(new OrderItem(product.id(), product.name(), product.price(), entry.getValue()));
        }

        long shipping = subtotal == 0 || subtotal >= FREE_SHIPPING_MINIMUM ? 0 : SHIPPING_FEE;
        long total = Math.addExact(subtotal, shipping);
        String id = "TM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant placedAt = Instant.now();
        jdbc.update("""
                INSERT INTO orders (id, customer_id, customer_name, customer_email, customer_phone,
                    delivery_address, delivery_city, delivery_postal_code, subtotal, shipping, total,
                    placed_at, payment_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, customer == null ? null : customer.id(), name, email, phone, address,
                city, postalCode, subtotal, shipping, total, Timestamp.from(placedAt), "NOT_PROCESSED");
        for (OrderItem item : items) {
            jdbc.update("""
                    INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity)
                    VALUES (?, ?, ?, ?, ?)
                    """, id, item.id(), item.name(), item.price(), item.quantity());
        }
        return new Order(id, placedAt.toString(), name, email, phone, address, city, postalCode,
                items, subtotal, shipping, total, "No payment collected");
    }

    @Transactional
    public void saveMessage(String rawName, String rawEmail, String rawMessage) {
        String name = requiredText(rawName, "Name", 2, 80);
        String email = normalizedEmail(rawEmail);
        String message = requiredText(rawMessage, "Message", 5, 2000);
        jdbc.update("INSERT INTO contact_messages (name, email, message, submitted_at) VALUES (?, ?, ?, ?)",
                name, email, message, Timestamp.from(Instant.now()));
    }

    private Order loadOrder(OrderHeader header) {
        List<OrderItem> items = jdbc.query("""
                SELECT product_id, product_name, unit_price, quantity
                FROM order_items WHERE order_id = ? ORDER BY id
                """, (row, index) -> new OrderItem(row.getString("product_id"),
                row.getString("product_name"), row.getLong("unit_price"), row.getInt("quantity")), header.id());
        return new Order(header.id(), header.placedAt(), header.name(), header.email(), header.phone(),
                header.address(), header.city(), header.postalCode(), items, header.subtotal(),
                header.shipping(), header.total(), "No payment collected");
    }

    private String createSession(long customerId) {
        byte[] tokenBytes = randomBytes(32);
        String token = HexFormat.of().formatHex(tokenBytes);
        jdbc.update("INSERT INTO auth_sessions (token_hash, customer_id, expires_at) VALUES (?, ?, ?)",
                digest(token), customerId, Timestamp.from(Instant.now().plus(Duration.ofDays(30))));
        return token;
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String passwordHash(String password, byte[] salt) {
        PBEKeySpec specification = new PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, 256);
        try {
            return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification).getEncoded());
        } catch (Exception exception) {
            throw new IllegalStateException("Password hashing is unavailable.", exception);
        } finally {
            specification.clearPassword();
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Session token hashing is unavailable.", exception);
        }
    }

    private static String requiredText(String value, String field, int minimum, int maximum) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < minimum || normalized.length() > maximum) {
            throw badRequest(field + " must be between " + minimum + " and " + maximum + " characters.");
        }
        return normalized;
    }

    private static String normalizedEmail(String value) {
        String email = requiredText(value, "Email", 3, 254).toLowerCase(java.util.Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw badRequest("Enter a valid email address.");
        return email;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw badRequest("Password must be between 8 and 128 characters.");
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record CustomerPassword(Customer customer, String salt, String hash) {}

    private record OrderHeader(String id, String name, String email, String phone, String address,
                               String city, String postalCode, long subtotal, long shipping, long total,
                               String placedAt) {}
}
