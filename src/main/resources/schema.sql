CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(40) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(40) NOT NULL,
    price BIGINT NOT NULL CHECK (price > 0),
    rating DOUBLE PRECISION NOT NULL,
    tag VARCHAR(40) NOT NULL,
    description VARCHAR(300) NOT NULL,
    image VARCHAR(500) NOT NULL
);

CREATE TABLE IF NOT EXISTS customers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_salt VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sessions_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(40) PRIMARY KEY,
    customer_id BIGINT,
    customer_name VARCHAR(80) NOT NULL,
    customer_email VARCHAR(254) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    delivery_address VARCHAR(160) NOT NULL,
    delivery_city VARCHAR(80) NOT NULL,
    delivery_postal_code VARCHAR(12) NOT NULL,
    subtotal BIGINT NOT NULL CHECK (subtotal >= 0),
    shipping BIGINT NOT NULL CHECK (shipping >= 0),
    total BIGINT NOT NULL CHECK (total >= 0),
    placed_at TIMESTAMP NOT NULL,
    payment_status VARCHAR(40) NOT NULL,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(40) NOT NULL,
    product_id VARCHAR(40) NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    unit_price BIGINT NOT NULL CHECK (unit_price > 0),
    quantity INT NOT NULL CHECK (quantity > 0),
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS contact_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    submitted_at TIMESTAMP NOT NULL
);
