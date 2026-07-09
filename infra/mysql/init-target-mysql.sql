CREATE DATABASE IF NOT EXISTS orders;

USE orders;

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO orders (order_no, customer_name, status, amount)
SELECT 'ORD-202607-001', 'demo-customer-a', 'PAID', 120000.00
WHERE NOT EXISTS (
    SELECT 1 FROM orders WHERE order_no = 'ORD-202607-001'
);

INSERT INTO orders (order_no, customer_name, status, amount)
SELECT 'ORD-202607-002', 'demo-customer-b', 'READY', 85000.00
WHERE NOT EXISTS (
    SELECT 1 FROM orders WHERE order_no = 'ORD-202607-002'
);

INSERT INTO order_items (order_id, product_name, quantity, price)
SELECT 1, 'sample-product-a', 2, 30000.00
WHERE NOT EXISTS (
    SELECT 1 FROM order_items WHERE order_id = 1 AND product_name = 'sample-product-a'
);

INSERT INTO order_items (order_id, product_name, quantity, price)
SELECT 1, 'sample-product-b', 1, 60000.00
WHERE NOT EXISTS (
    SELECT 1 FROM order_items WHERE order_id = 1 AND product_name = 'sample-product-b'
);