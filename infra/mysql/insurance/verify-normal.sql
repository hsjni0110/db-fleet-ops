USE insurance_sandbox;

DROP PROCEDURE IF EXISTS verify_insurance_sandbox;

DELIMITER //

CREATE PROCEDURE verify_insurance_sandbox()
BEGIN
    DECLARE actual_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO actual_count
    FROM information_schema.tables
    WHERE table_schema = 'insurance_sandbox'
      AND table_name IN ('customer', 'policy', 'premium_payment', 'claim', 'claim_payment');
    IF actual_count <> 5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insurance Sandbox must have five domain tables';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = 'insurance_sandbox'
      AND constraint_name IN (
          'fk_policy_customer',
          'fk_premium_payment_policy',
          'fk_claim_policy',
          'fk_claim_payment_claim'
      );
    IF actual_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insurance Sandbox foreign keys are incomplete';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = 'insurance_sandbox'
      AND constraint_type = 'UNIQUE'
      AND constraint_name IN ('uk_customer_number', 'uk_policy_number', 'uk_claim_number');
    IF actual_count <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insurance Sandbox unique constraints are incomplete';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = 'insurance_sandbox'
      AND constraint_type = 'CHECK'
      AND constraint_name IN (
          'chk_policy_status',
          'chk_policy_period',
          'chk_policy_premium_amount',
          'chk_premium_payment_installment',
          'chk_premium_payment_amount',
          'chk_premium_payment_status',
          'chk_premium_payment_completed_at',
          'chk_claim_claimed_amount',
          'chk_claim_approved_amount',
          'chk_claim_status',
          'chk_claim_payment_amount',
          'chk_claim_payment_status',
          'chk_claim_payment_completed_at'
      );
    IF actual_count <> 13 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insurance Sandbox check constraints are incomplete';
    END IF;

    SELECT COUNT(DISTINCT index_name) INTO actual_count
    FROM information_schema.statistics
    WHERE table_schema = 'insurance_sandbox'
      AND index_name IN (
          'idx_policy_customer_status',
          'idx_premium_payment_policy_installment_status',
          'idx_claim_policy_status',
          'idx_claim_payment_claim_status'
      );
    IF actual_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insurance Sandbox indexes are incomplete';
    END IF;

    SELECT COUNT(*) INTO actual_count FROM customer;
    IF actual_count <> 10000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Customer seed count must be 10000';
    END IF;

    SELECT COUNT(*) INTO actual_count FROM policy;
    IF actual_count <> 30000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Policy seed count must be 30000';
    END IF;

    SELECT COUNT(*) INTO actual_count FROM premium_payment;
    IF actual_count <> 300000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'PremiumPayment seed count must be 300000';
    END IF;

    SELECT COUNT(*) INTO actual_count FROM claim;
    IF actual_count <> 20000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Claim seed count must be 20000';
    END IF;

    SELECT COUNT(*) INTO actual_count FROM claim_payment;
    IF actual_count <> 10000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ClaimPayment seed count must be 10000';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM (
        SELECT policy_id, installment_number
        FROM premium_payment
        WHERE status = 'COMPLETED'
        GROUP BY policy_id, installment_number
        HAVING COUNT(*) > 1
    ) duplicate_premium_payment;
    IF actual_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate completed PremiumPayment found';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM (
        SELECT claim_id
        FROM claim_payment
        WHERE status = 'COMPLETED'
        GROUP BY claim_id
        HAVING COUNT(*) > 1
    ) duplicate_claim_payment;
    IF actual_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate completed ClaimPayment found';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM claim c
    LEFT JOIN policy p ON p.policy_id = c.policy_id
    WHERE p.policy_id IS NULL;
    IF actual_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Claim without Policy found';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM claim_payment
    WHERE status = 'COMPLETED' AND paid_amount <= 0;
    IF actual_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Completed ClaimPayment with non-positive amount found';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM policy p
    LEFT JOIN customer c ON c.customer_id = p.customer_id
    WHERE p.status = 'ACTIVE' AND c.customer_id IS NULL;
    IF actual_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Active Policy without Customer found';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM claim c
    LEFT JOIN claim_payment cp
      ON cp.claim_id = c.claim_id AND cp.status = 'COMPLETED'
    WHERE c.status = 'PAID' AND cp.claim_payment_id IS NULL;
    IF actual_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Paid Claim without completed ClaimPayment found';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM claim_payment cp
    JOIN claim c ON c.claim_id = cp.claim_id
    WHERE cp.status = 'COMPLETED'
      AND (c.approved_amount IS NULL OR cp.paid_amount > c.approved_amount);
    IF actual_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ClaimPayment exceeds approved Claim amount';
    END IF;

    SELECT 'NORMAL' AS sandbox_state,
           10000 AS customer_count,
           30000 AS policy_count,
           300000 AS premium_payment_count,
           20000 AS claim_count,
           10000 AS claim_payment_count;
END//

DELIMITER ;

CALL verify_insurance_sandbox();
DROP PROCEDURE verify_insurance_sandbox;
