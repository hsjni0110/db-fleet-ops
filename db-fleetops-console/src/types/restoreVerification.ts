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
}