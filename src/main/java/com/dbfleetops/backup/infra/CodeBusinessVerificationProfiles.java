package com.dbfleetops.backup.infra;

import com.dbfleetops.backup.domain.BusinessRule;
import com.dbfleetops.backup.domain.BusinessVerificationProfile;
import com.dbfleetops.backup.domain.BusinessVerificationProfiles;
import com.dbfleetops.database.domain.DatabaseEngine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CodeBusinessVerificationProfiles implements BusinessVerificationProfiles {

    public static final String INSURANCE_SANDBOX_V1 = "INSURANCE_SANDBOX_V1";
    private static final long RULE_TIMEOUT_MILLIS = 5_000L;

    private final Map<String, BusinessVerificationProfile> profiles;

    public CodeBusinessVerificationProfiles() {
        BusinessVerificationProfile insurance = new BusinessVerificationProfile(
                INSURANCE_SANDBOX_V1,
                "Insurance Sandbox V1",
                "Insurance Sandbox의 계약·수납·청구·지급 업무 불변식을 검증합니다.",
                DatabaseEngine.MYSQL,
                List.of("customer", "policy", "premium_payment", "claim", "claim_payment"),
                insuranceRules());
        this.profiles = Map.of(insurance.key(), insurance);
    }

    @Override
    public Optional<BusinessVerificationProfile> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(key));
    }

    @Override
    public List<BusinessVerificationProfile> findAll() {
        return profiles.values().stream().sorted(java.util.Comparator.comparing(
                BusinessVerificationProfile::key)).toList();
    }

    private List<BusinessRule> insuranceRules() {
        return List.of(
                rule("DUPLICATE_COMPLETED_PREMIUM_PAYMENT", "완료된 보험료 수납 중복", """
                        SELECT COUNT(*) AS violation_count
                        FROM (
                            SELECT policy_id, installment_number
                            FROM premium_payment
                            WHERE status = 'COMPLETED'
                            GROUP BY policy_id, installment_number
                            HAVING COUNT(*) > 1
                        ) violation
                        """),
                rule("DUPLICATE_COMPLETED_CLAIM_PAYMENT", "완료된 보험금 지급 중복", """
                        SELECT COUNT(*) AS violation_count
                        FROM (
                            SELECT claim_id
                            FROM claim_payment
                            WHERE status = 'COMPLETED'
                            GROUP BY claim_id
                            HAVING COUNT(*) > 1
                        ) violation
                        """),
                rule("CLAIM_WITHOUT_POLICY", "보험 계약 없는 보험금 청구", """
                        SELECT COUNT(*) AS violation_count
                        FROM claim c
                        LEFT JOIN policy p ON p.policy_id = c.policy_id
                        WHERE p.policy_id IS NULL
                        """),
                rule("NON_POSITIVE_COMPLETED_CLAIM_PAYMENT", "0 이하 완료 보험금 지급", """
                        SELECT COUNT(*) AS violation_count
                        FROM claim_payment
                        WHERE status = 'COMPLETED' AND paid_amount <= 0
                        """),
                rule("PAID_CLAIM_WITHOUT_COMPLETED_PAYMENT", "완료 지급 없는 지급 완료 청구", """
                        SELECT COUNT(*) AS violation_count
                        FROM claim c
                        LEFT JOIN claim_payment cp
                          ON cp.claim_id = c.claim_id AND cp.status = 'COMPLETED'
                        WHERE c.status = 'PAID' AND cp.claim_payment_id IS NULL
                        """),
                rule("ACTIVE_POLICY_WITHOUT_CUSTOMER", "고객 없는 활성 보험 계약", """
                        SELECT COUNT(*) AS violation_count
                        FROM policy p
                        LEFT JOIN customer c ON c.customer_id = p.customer_id
                        WHERE p.status = 'ACTIVE' AND c.customer_id IS NULL
                        """),
                rule("CLAIM_PAYMENT_EXCEEDS_APPROVED_AMOUNT", "승인금액 초과 보험금 지급", """
                        SELECT COUNT(*) AS violation_count
                        FROM claim_payment cp
                        JOIN claim c ON c.claim_id = cp.claim_id
                        WHERE cp.status = 'COMPLETED'
                          AND (c.approved_amount IS NULL OR cp.paid_amount > c.approved_amount)
                        """));
    }

    private BusinessRule rule(String code, String name, String query) {
        return new BusinessRule(code, name, query.strip(), RULE_TIMEOUT_MILLIS);
    }
}
