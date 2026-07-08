CREATE DATABASE IF NOT EXISTS orders
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE orders;

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(50) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_orders
        FOREIGN KEY (order_id)
        REFERENCES orders (id)
);

INSERT INTO orders (
    order_no,
    customer_name,
    status,
    total_amount
)
VALUES
    ('ORD-2026-0001', 'kim', 'PAID', 12000.00),
    ('ORD-2026-0002', 'lee', 'READY', 26500.00),
    ('ORD-2026-0003', 'park', 'SHIPPED', 48000.00);

INSERT INTO order_items (
    order_id,
    product_name,
    quantity,
    unit_price
)
VALUES
    (1, 'keyboard', 1, 12000.00),
    (2, 'mouse', 2, 13250.00),
    (3, 'monitor', 1, 48000.00);