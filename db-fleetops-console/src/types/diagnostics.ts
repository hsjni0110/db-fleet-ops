import type { DatabaseEngine, DateTimeString, Nullable } from "./common";

export interface DatabaseVersionResponse {
  databaseId: number;
  engine: DatabaseEngine;
  version: string;
}

export interface DatabaseUptimeResponse {
  databaseId: number;
  engine: DatabaseEngine;
  uptimeSeconds: number;
}

export interface ConnectionSummaryResponse {
  databaseId: number;
  engine: DatabaseEngine;
  currentConnections: number;
  runningConnections: number;
  maxConnections: number;
  usagePercent: number;
}

export interface DatabaseSessionResponse {
  databaseId: number;
  engine: DatabaseEngine;
  processId: number;
  user: string;
  host: string;
  databaseName: Nullable<string>;
  command: string;
  timeSeconds: number;
  state: Nullable<string>;
  queryPreview: Nullable<string>;
}

export interface LongTransactionResponse {
  databaseId: number;
  engine: DatabaseEngine;
  transactionId: string;
  state: string;
  startedAt: DateTimeString;
  durationSeconds: number;
  threadId: number;
  queryPreview: Nullable<string>;
}

export interface LockWaitResponse {
  databaseId: number;
  engine: DatabaseEngine;
  waitingTransactionId: string;
  waitingThreadId: number;
  waitingQueryPreview: Nullable<string>;
  blockingTransactionId: string;
  blockingThreadId: number;
  blockingQueryPreview: Nullable<string>;
}

export interface SlowQueryResponse {
  databaseId: number;
  engine: DatabaseEngine;
  digestText: string;
  executionCount: number;
  averageSeconds: number;
  maxSeconds: number;
  rowsExamined: number;
  rowsSent: number;
}