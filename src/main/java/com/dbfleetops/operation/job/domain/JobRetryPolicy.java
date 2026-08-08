package com.dbfleetops.operation.job.domain;

/** 실패한 Job을 다시 대기 상태로 돌릴 수 있는지 판단합니다. */
public class JobRetryPolicy {

    public boolean canRetry(OperationJob job, boolean retryable) {
        return retryable && job.getRetryCount() < job.getMaxRetryCount();
    }
}
