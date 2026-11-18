CREATE DATABASE IF NOT EXISTS insurance_sandbox
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE insurance_sandbox;

CREATE TABLE IF NOT EXISTS customer (
    customer_id BIGINT NOT NULL,
    customer_number VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (customer_id),
    CONSTRAINT uk_customer_number UNIQUE (customer_number)
);

CREATE TABLE IF NOT EXISTS policy (
    policy_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    policy_number VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    effective_date DATE NOT NULL,
    expiration_date DATE NOT NULL,
    premium_amount DECIMAL(15, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (policy_id),
    CONSTRAINT uk_policy_number UNIQUE (policy_number),
    CONSTRAINT fk_policy_customer FOREIGN KEY (customer_id)
        REFERENCES customer (customer_id),
    CONSTRAINT chk_policy_status CHECK (
        status IN ('APPLICATION', 'UNDERWRITING', 'ACTIVE', 'LAPSED', 'TERMINATED', 'MATURED')
    ),
    CONSTRAINT chk_policy_period CHECK (expiration_date > effective_date),
    CONSTRAINT chk_policy_premium_amount CHECK (premium_amount > 0),
    INDEX idx_policy_customer_status (customer_id, status)
);

CREATE TABLE IF NOT EXISTS premium_payment (
    premium_payment_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    paid_at DATETIME NULL,
    amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (premium_payment_id),
    CONSTRAINT fk_premium_payment_policy FOREIGN KEY (policy_id)
        REFERENCES policy (policy_id),
    CONSTRAINT chk_premium_payment_installment CHECK (installment_number > 0),
    CONSTRAINT chk_premium_payment_amount CHECK (amount >= 0),
    CONSTRAINT chk_premium_payment_status CHECK (
        status IN ('SCHEDULED', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_premium_payment_completed_at CHECK (
        status <> 'COMPLETED' OR paid_at IS NOT NULL
    ),
    INDEX idx_premium_payment_policy_installment_status
        (policy_id, installment_number, status)
);

CREATE TABLE IF NOT EXISTS claim (
    claim_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    claim_number VARCHAR(32) NOT NULL,
    claimed_amount DECIMAL(15, 2) NOT NULL,
    approved_amount DECIMAL(15, 2) NULL,
    status VARCHAR(20) NOT NULL,
    received_at DATETIME NOT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (claim_id),
    CONSTRAINT uk_claim_number UNIQUE (claim_number),
    CONSTRAINT fk_claim_policy FOREIGN KEY (policy_id)
        REFERENCES policy (policy_id),
    CONSTRAINT chk_claim_claimed_amount CHECK (claimed_amount > 0),
    CONSTRAINT chk_claim_approved_amount CHECK (
        approved_amount IS NULL
        OR (approved_amount >= 0 AND approved_amount <= claimed_amount)
    ),
    CONSTRAINT chk_claim_status CHECK (
        status IN ('RECEIVED', 'REVIEWING', 'APPROVED', 'REJECTED', 'PAID')
    ),
    INDEX idx_claim_policy_status (policy_id, status)
);

CREATE TABLE IF NOT EXISTS claim_payment (
    claim_payment_id BIGINT NOT NULL,
    claim_id BIGINT NOT NULL,
    paid_amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (claim_payment_id),
    CONSTRAINT fk_claim_payment_claim FOREIGN KEY (claim_id)
        REFERENCES claim (claim_id),
    CONSTRAINT chk_claim_payment_amount CHECK (paid_amount >= 0),
    CONSTRAINT chk_claim_payment_status CHECK (
        status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_claim_payment_completed_at CHECK (
        status <> 'COMPLETED' OR paid_at IS NOT NULL
    ),
    INDEX idx_claim_payment_claim_status (claim_id, status)
);

-- A deterministic number source keeps the full seed set-based and reproducible.
DROP TABLE IF EXISTS insurance_seed_number;
DROP TABLE IF EXISTS insurance_seed_digit;

CREATE TABLE insurance_seed_digit (
    digit INT NOT NULL PRIMARY KEY
);

INSERT INTO insurance_seed_digit (digit)
VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

CREATE TABLE insurance_seed_number (
    number INT NOT NULL PRIMARY KEY
);

INSERT INTO insurance_seed_number (number)
SELECT ones.digit
       + tens.digit * 10
       + hundreds.digit * 100
       + thousands.digit * 1000
       + ten_thousands.digit * 10000
       + hundred_thousands.digit * 100000
       + 1
FROM insurance_seed_digit ones
CROSS JOIN insurance_seed_digit tens
CROSS JOIN insurance_seed_digit hundreds
CROSS JOIN insurance_seed_digit thousands
CROSS JOIN insurance_seed_digit ten_thousands
CROSS JOIN insurance_seed_digit hundred_thousands
WHERE ones.digit
      + tens.digit * 10
      + hundreds.digit * 100
      + thousands.digit * 1000
      + ten_thousands.digit * 10000
      + hundred_thousands.digit * 100000 < 300000;

INSERT IGNORE INTO customer (
    customer_id, customer_number, name, created_at
)
SELECT number,
       CONCAT('SYN-CUSTOMER-', LPAD(number, 5, '0')),
       CONCAT('Synthetic Customer ', LPAD(number, 5, '0')),
       DATE_ADD('2024-01-01 00:00:00', INTERVAL MOD(number - 1, 365) DAY)
FROM insurance_seed_number
WHERE number <= 10000;

INSERT IGNORE INTO policy (
    policy_id, customer_id, policy_number, status, effective_date, expiration_date,
    premium_amount, created_at
)
SELECT number,
       MOD(number - 1, 10000) + 1,
       CONCAT('POL-', LPAD(number, 8, '0')),
       CASE MOD(number - 1, 10)
           WHEN 0 THEN 'APPLICATION'
           WHEN 1 THEN 'UNDERWRITING'
           WHEN 7 THEN 'LAPSED'
           WHEN 8 THEN 'TERMINATED'
           WHEN 9 THEN 'MATURED'
           ELSE 'ACTIVE'
       END,
       DATE_ADD('2024-01-01', INTERVAL MOD(number - 1, 365) DAY),
       DATE_ADD(DATE_ADD('2024-01-01', INTERVAL MOD(number - 1, 365) DAY), INTERVAL 365 DAY),
       50000.00 + MOD(number - 1, 20) * 2500.00,
       DATE_ADD('2024-01-01 00:00:00', INTERVAL MOD(number - 1, 365) DAY)
FROM insurance_seed_number
WHERE number <= 30000;

INSERT IGNORE INTO premium_payment (
    premium_payment_id, policy_id, installment_number, due_date, paid_at, amount, status, created_at
)
SELECT number,
       FLOOR((number - 1) / 10) + 1,
       MOD(number - 1, 10) + 1,
       DATE_ADD('2024-02-01', INTERVAL MOD(number - 1, 10) MONTH),
       CASE
           WHEN MOD(number - 1, 10) < 8
               THEN DATE_ADD('2024-02-01 09:00:00', INTERVAL MOD(number - 1, 10) MONTH)
           ELSE NULL
       END,
       50000.00 + MOD(FLOOR((number - 1) / 10), 20) * 2500.00,
       CASE MOD(number - 1, 10)
           WHEN 8 THEN 'SCHEDULED'
           WHEN 9 THEN CASE WHEN MOD(FLOOR((number - 1) / 10), 2) = 0
                            THEN 'FAILED' ELSE 'CANCELLED' END
           ELSE 'COMPLETED'
       END,
       DATE_ADD('2024-01-01 00:00:00', INTERVAL MOD(number - 1, 365) DAY)
FROM insurance_seed_number
WHERE number <= 300000;

INSERT IGNORE INTO claim (
    claim_id, policy_id, claim_number, claimed_amount, approved_amount, status,
    received_at, reviewed_at, created_at
)
SELECT number,
       number,
       CONCAT('CLM-', LPAD(number, 8, '0')),
       100000.00 + MOD(number - 1, 50) * 10000.00,
       CASE
           WHEN number <= 12000
               THEN 100000.00 + MOD(number - 1, 50) * 10000.00
           WHEN number > 18000 THEN 0.00
           ELSE NULL
       END,
       CASE
           WHEN number <= 10000 THEN 'PAID'
           WHEN number <= 12000 THEN 'APPROVED'
           WHEN number <= 16000 THEN 'REVIEWING'
           WHEN number <= 18000 THEN 'RECEIVED'
           ELSE 'REJECTED'
       END,
       DATE_ADD('2025-01-01 09:00:00', INTERVAL MOD(number - 1, 365) DAY),
       CASE
           WHEN number <= 12000 OR number > 18000
               THEN DATE_ADD('2025-01-02 09:00:00', INTERVAL MOD(number - 1, 365) DAY)
           ELSE NULL
       END,
       DATE_ADD('2025-01-01 09:00:00', INTERVAL MOD(number - 1, 365) DAY)
FROM insurance_seed_number
WHERE number <= 20000;

INSERT IGNORE INTO claim_payment (
    claim_payment_id, claim_id, paid_amount, status, paid_at, created_at
)
SELECT number,
       number,
       100000.00 + MOD(number - 1, 50) * 10000.00,
       'COMPLETED',
       DATE_ADD('2025-01-05 09:00:00', INTERVAL MOD(number - 1, 365) DAY),
       DATE_ADD('2025-01-05 09:00:00', INTERVAL MOD(number - 1, 365) DAY)
FROM insurance_seed_number
WHERE number <= 10000;

DROP TABLE insurance_seed_number;
DROP TABLE insurance_seed_digit;
