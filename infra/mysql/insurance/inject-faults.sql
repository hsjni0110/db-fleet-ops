USE insurance_sandbox;

-- Fault IDs are reserved so injection is idempotent and reset removes only fault data.
INSERT IGNORE INTO premium_payment (
    premium_payment_id, policy_id, installment_number, due_date, paid_at, amount, status, created_at
)
VALUES (
    900000001, 1, 1, '2024-02-01', '2024-02-01 10:00:00', 50000.00, 'COMPLETED',
    '2026-01-01 00:00:00'
);

INSERT IGNORE INTO claim_payment (
    claim_payment_id, claim_id, paid_amount, status, paid_at, created_at
)
VALUES
    (900000001, 1, 100000.00, 'COMPLETED', '2025-01-06 09:00:00', '2026-01-01 00:00:00'),
    (900000002, 10001, 0.00, 'COMPLETED', '2025-01-06 09:00:00', '2026-01-01 00:00:00');

-- Only the two structural corruption fixtures bypass foreign-key checks.
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO claim (
    claim_id, policy_id, claim_number, claimed_amount, approved_amount, status,
    received_at, reviewed_at, created_at
)
VALUES (
    900000001, 900000002, 'FAULT-CLM-MISSING-POLICY', 100000.00, NULL, 'RECEIVED',
    '2026-01-01 09:00:00', NULL, '2026-01-01 09:00:00'
);

INSERT IGNORE INTO policy (
    policy_id, customer_id, policy_number, status, effective_date, expiration_date,
    premium_amount, created_at
)
VALUES (
    900000001, 900000001, 'FAULT-POL-MISSING-CUSTOMER', 'ACTIVE',
    '2026-01-01', '2027-01-01', 50000.00, '2026-01-01 00:00:00'
);

SET FOREIGN_KEY_CHECKS = 1;
