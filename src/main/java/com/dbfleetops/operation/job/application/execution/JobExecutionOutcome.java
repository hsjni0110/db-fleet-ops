package com.dbfleetops.operation.job.application.execution;

/** Job 종류별 실행을 시작한 결과입니다. */
public record JobExecutionOutcome(JobExecutionStatus status, String resultCode,
        String resultMessage, boolean retryable) {

    public static JobExecutionOutcome inProgress(String message) {
        return new JobExecutionOutcome(JobExecutionStatus.IN_PROGRESS, null, message, false);
    }

    public static JobExecutionOutcome succeeded(String message) {
        return new JobExecutionOutcome(JobExecutionStatus.SUCCEEDED, "SUCCESS", message, false);
    }

    public static JobExecutionOutcome failed(String code, String message, boolean retryable) {
        return new JobExecutionOutcome(JobExecutionStatus.FAILED, code, message, retryable);
    }
}
