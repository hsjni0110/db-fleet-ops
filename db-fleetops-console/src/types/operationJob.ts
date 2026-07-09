export type OperationJobType =
  | "BACKUP"
  | "CONFIGURATION_CHECK"
  | "CONFIGURATION_APPLY"
  | "RESTART";

export type OperationJobStatus =
  | "QUEUED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "TIMED_OUT";

export type OperationTaskType =
  | "COLLECT_LINUX_STATUS"
  | "MYSQL_LOGICAL_BACKUP"
  | "MYSQL_RESTORE_VERIFY"
  | "CONFIGURATION_CHECK";

export type OperationTaskStatus =
  | "QUEUED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED";

export interface OperationJobResponse {
  jobId: number;
  jobType: OperationJobType;
  status: OperationJobStatus;
  targetDatabaseId: number;
  requestedBy: string;
  retryCount: number;
  maxRetryCount: number;
  leaseOwner?: string | null;
  leaseUntil?: string | null;
  availableAt?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  resultCode?: string | null;
  resultMessage?: string | null;
  createdAt: string;
}

export interface BackupJobRequest {
  reason: string;
  requestedBy: string;
}

export interface ConfigurationCheckJobRequest {
  profileId: number;
  requestedBy: string;
  reason: string;
}
