import type { DatabaseEngine, DatabaseEnvironment, DateTimeString, Nullable } from "./common";

export type DatabaseStatus = "ACTIVE" | "INACTIVE";

export type DatabaseHealthStatus =
  | "HEALTHY"
  | "DEGRADED"
  | "CRITICAL"
  | "UNKNOWN"
  | "UP"
  | "DOWN";

export interface CreateDatabaseInstanceRequest {
  name: string;
  host: string;
  port: number;
  databaseName: string;
  engine: DatabaseEngine;
  environment: DatabaseEnvironment;
  serviceName?: string;
  owner?: string;
  description?: string;
  username: string;
  password: string;
  businessVerificationProfileKey?: Nullable<string>;
}

export type UpdateDatabaseInstanceRequest = CreateDatabaseInstanceRequest;

export interface DatabaseInstanceResponse {
  id: number;
  name: string;
  host?: string;
  port?: number;
  databaseName?: string;
  engine: DatabaseEngine;
  status: DatabaseStatus;
  environment?: DatabaseEnvironment;
  serviceName?: Nullable<string>;
  owner?: Nullable<string>;
  description?: Nullable<string>;
  businessVerificationProfileKey?: Nullable<string>;
}

export interface DatabaseInstanceSummary {
  id: number;
  name: string;
  engine: DatabaseEngine;
  status: DatabaseStatus;
  businessVerificationProfileKey?: Nullable<string>;
}

export interface BusinessVerificationProfileResponse {
  key: string;
  name: string;
  description: string;
  engine: DatabaseEngine;
  requiredTables: string[];
  ruleCount: number;
}

export interface DefaultDatabaseHealthResponse {
  databaseType: DatabaseEngine;
  status: "UP" | "DOWN";
  latencyMs: number;
}

export interface InventoryHealthCheckResponse {
  databaseId: number;
  status: DatabaseHealthStatus;
  connectionSuccess: boolean;
  responseTimeMs: number;
  message: string;
  checkedAt?: DateTimeString;
}
