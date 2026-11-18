import type { DateTimeString, Nullable } from "./common";

export type RestoreVerificationStatus =
  | "REQUESTED"
  | "RUNNING"
  | "VERIFIED"
  | "FAILED"
  | "CLEANUP_FAILED";

export type RestoreVerificationItemStatus =
  | "VERIFIED"
  | "MISSING"
  | "COUNT_FAILED"
  | "SKIPPED";

export type TechnicalVerificationStatus = "VERIFIED" | "FAILED";
export type BusinessVerificationStatus = "NOT_APPLIED" | "SKIPPED" | "VERIFIED" | "FAILED" | "ERROR";
export type BusinessRuleStatus = "VERIFIED" | "FAILED" | "ERROR";
export type CleanupStatus = "NOT_REQUESTED" | "COMPLETED" | "FAILED" | "SKIPPED";

export interface BusinessRuleResultResponse {
  ruleCode: string;
  ruleName: string;
  status: BusinessRuleStatus;
  violationCount: Nullable<number>;
  durationMillis: number;
  message: Nullable<string>;
}

export interface BusinessVerificationResponse {
  profileKey: Nullable<string>;
  status: BusinessVerificationStatus;
  checkedRuleCount: number;
  failedRuleCount: number;
  ruleResults: BusinessRuleResultResponse[];
}

export interface RestoreVerificationItemResponse {
  id: number;
  verificationId: number;
  tableName: string;
  existsInRestoredDb: boolean;
  rowCount: Nullable<number>;
  status: RestoreVerificationItemStatus;
  message: Nullable<string>;
  createdAt: DateTimeString;
}

export interface RestoreVerificationResponse {
  id: number;
  operationJobId: number;
  backupTaskId: number;
  restoreVerifyTaskId: number;
  databaseId: number;
  sourceDatabaseName: string;
  backupFile: string;
  temporaryDatabaseName: string;
  status: RestoreVerificationStatus;
  restoredTableCount: number;
  checkedTableCount: number;
  totalRowCount: number;
  errorCode: Nullable<string>;
  errorMessage: Nullable<string>;
  startedAt: Nullable<DateTimeString>;
  completedAt: Nullable<DateTimeString>;
  createdAt: DateTimeString;
  items: RestoreVerificationItemResponse[];
  technicalStatus?: TechnicalVerificationStatus;
  cleanupStatus?: CleanupStatus;
  cleanupErrorCode?: Nullable<string>;
  cleanupErrorMessage?: Nullable<string>;
  businessVerification?: BusinessVerificationResponse;
}
