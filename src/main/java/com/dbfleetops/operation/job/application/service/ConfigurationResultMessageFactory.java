package com.dbfleetops.operation.job.application.service;

import com.dbfleetops.operation.job.application.required.ConfigurationApplyOutcome;
import com.dbfleetops.operation.job.application.required.ConfigurationCheckOutcome;
import org.springframework.stereotype.Component;

/** Worker가 Job에 저장할 설정 작업 결과 문구를 만듭니다. */
@Component
public class ConfigurationResultMessageFactory {

    public String configurationCheck(ConfigurationCheckOutcome outcome) {
        return "Configuration check completed. driftId=" + outcome.driftId()
                + ", status=" + outcome.status();
    }

    public String configurationApply(ConfigurationApplyOutcome outcome) {
        return "Configuration apply completed. applyId=" + outcome.applyId()
                + ", status=" + outcome.status()
                + ", successCount=" + outcome.successCount()
                + ", failedCount=" + outcome.failedCount()
                + ", skippedCount=" + outcome.skippedCount();
    }

    public String failure(Exception exception, String defaultMessage) {
        return exception.getMessage() == null ? defaultMessage : exception.getMessage();
    }
}
