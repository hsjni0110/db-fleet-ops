USE insurance_sandbox;

DELETE FROM claim_payment
WHERE claim_payment_id IN (900000001, 900000002);

DELETE FROM premium_payment
WHERE premium_payment_id = 900000001;

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM claim
WHERE claim_id = 900000001;

DELETE FROM policy
WHERE policy_id = 900000001;

SET FOREIGN_KEY_CHECKS = 1;
