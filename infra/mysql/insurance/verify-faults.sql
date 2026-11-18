USE insurance_sandbox;

DROP PROCEDURE IF EXISTS verify_insurance_faults;

DELIMITER //

CREATE PROCEDURE verify_insurance_faults()
BEGIN
    DECLARE actual_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO actual_count FROM customer;
    IF actual_count <> 10000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fault injection changed Customer count unexpectedly';
    END IF;

    SELECT COUNT(*) INTO actual_count FROM policy;
    IF actual_count <> 30001 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fault Policy must be injected exactly once';
    END IF;

    SELECT COUNT(*) INTO actual_count FROM premium_payment;
    IF actual_count <> 300001 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fault PremiumPayment must be injected exactly once';
    END IF;

    SELECT COUNT(*) INTO actual_count FROM claim;
    IF actual_count <> 20001 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fault Claim must be injected exactly once';
    END IF;

    SELECT COUNT(*) INTO actual_count FROM claim_payment;
    IF actual_count <> 10002 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fault ClaimPayment must be injected exactly twice';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM (
        SELECT policy_id, installment_number
        FROM premium_payment
        WHERE status = 'COMPLETED'
        GROUP BY policy_id, installment_number
        HAVING COUNT(*) > 1
    ) violation;
    IF actual_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected one duplicate PremiumPayment violation';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM (
        SELECT claim_id
        FROM claim_payment
        WHERE status = 'COMPLETED'
        GROUP BY claim_id
        HAVING COUNT(*) > 1
    ) violation;
    IF actual_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected one duplicate ClaimPayment violation';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM claim c
    LEFT JOIN policy p ON p.policy_id = c.policy_id
    WHERE p.policy_id IS NULL;
    IF actual_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected one Claim without Policy violation';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM claim_payment
    WHERE status = 'COMPLETED' AND paid_amount <= 0;
    IF actual_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected one zero ClaimPayment violation';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM policy p
    LEFT JOIN customer c ON c.customer_id = p.customer_id
    WHERE p.status = 'ACTIVE' AND c.customer_id IS NULL;
    IF actual_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected one active Policy without Customer violation';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM claim c
    LEFT JOIN claim_payment cp
      ON cp.claim_id = c.claim_id AND cp.status = 'COMPLETED'
    WHERE c.status = 'PAID' AND cp.claim_payment_id IS NULL;
    IF actual_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Paid Claim without completed ClaimPayment must remain valid';
    END IF;

    SELECT COUNT(*) INTO actual_count
    FROM claim_payment cp
    JOIN claim c ON c.claim_id = cp.claim_id
    WHERE cp.status = 'COMPLETED'
      AND (c.approved_amount IS NULL OR cp.paid_amount > c.approved_amount);
    IF actual_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ClaimPayment approved amount Rule must remain valid';
    END IF;

    SELECT 'FAULTED' AS sandbox_state,
           1 AS duplicate_premium_payment,
           1 AS duplicate_claim_payment,
           1 AS claim_without_policy,
           1 AS zero_claim_payment,
           1 AS active_policy_without_customer,
           0 AS paid_claim_without_completed_payment,
           0 AS claim_payment_exceeds_approved_amount;
END//

DELIMITER ;

CALL verify_insurance_faults();
DROP PROCEDURE verify_insurance_faults;
