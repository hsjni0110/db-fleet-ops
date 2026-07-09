import type { DateTimeString, Nullable } from "./common";

export type OperationJobType =
  | "BACKUP"
  | "CONFIGURATION_CHECK"
  | "CONFIGURATION_APPLY"
  | string;

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
  | string;

export type OperationTaskStatus =
  | "QUEUED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED";

export interface CreateBackupJobRequest {
  reason: string;
  requestedBy: string;
}

export interface OperationJobResponse {
  jobId: number;
  jobType: OperationJobType;
  status: OperationJobStatus;
  targetDatabaseId: number;
  requestedBy: string;
  retryCount: number;
  maxRetryCount: number;
  leaseOwner: Nullable<string>;
  leaseUntil: Nullable<DateTimeString>;
  availableAt: Nullable<DateTimeString>;
  startedAt: Nullable<DateTimeString>;
  finishedAt: Nullable<DateTimeString>;
  resultCode: Nullable<string>;
  resultMessage: Nullable<string>;
  createdAt: DateTimeString;
  idempotencyKey?: Nullable<string>;
  requestPayload?: Nullable<string>;
}

export interface OperationTaskResponse {
  taskId: number;
  agentId: number;
  operationJobId: Nullable<number>;
  taskType: OperationTaskType;
  status: OperationTaskStatus;
  parametersJson: Nullable<string>;
  resultPayloadJson: Nullable<string>;
  errorCode: Nullable<string>;
  errorMessage: Nullable<string>;
  startedAt: Nullable<DateTimeString>;
  completedAt: Nullable<DateTimeString>;
  createdAt: DateTimeString;
}

export interface ClaimJobResponse {
  claimed: boolean;
  jobId: Nullable<number>;
  jobType: Nullable<OperationJobType>;
  status: Nullable<OperationJobStatus>;
  targetDatabaseId: Nullable<number>;
  leaseOwner: Nullable<string>;
  leaseUntil: Nullable<DateTimeString>;
}