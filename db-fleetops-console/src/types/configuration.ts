import type { DatabaseEngine, DatabaseEnvironment, DateTimeString, Nullable } from "./common";
import type { OperationJobResponse } from "./operationJob";

export type ConfigurationProfileStatus = "DRAFT" | "ACTIVE" | "INACTIVE";

export type ParameterValueType = "STRING" | "NUMBER" | "BOOLEAN";

export type ComplianceStatus = "COMPLIANT" | "NON_COMPLIANT" | "MISSING";

export type ConfigurationApplyStatus =
  | "REQUESTED"
  | "RUNNING"
  | "SUCCEEDED"
  | "PARTIALLY_SUCCEEDED"
  | "FAILED"
  | "CANCELLED";

export type ConfigurationApplyItemStatus =
  | "PENDING"
  | "APPLIED"
  | "VERIFIED"
  | "SKIPPED"
  | "FAILED"
  | "UNSUPPORTED";

export interface CreateConfigurationProfileRequest {
  profileName: string;
  engineType: DatabaseEngine;
  environment: DatabaseEnvironment;
  versionRange?: string;
  description?: string;
}

export interface ConfigurationProfileParameterResponse {
  parameterId: number;
  profileId: number;
  parameterName: string;
  expectedValue: string;
  valueType: ParameterValueType;
  required: boolean;
  dynamic: boolean;
  applyAllowed: boolean;
  description: Nullable<string>;
}

export interface ConfigurationProfileResponse {
  profileId: number;
  profileName: string;
  engineType: DatabaseEngine;
  environment: DatabaseEnvironment;
  versionRange: Nullable<string>;
  description: Nullable<string>;
  status: ConfigurationProfileStatus;
  parameters: ConfigurationProfileParameterResponse[];
}

export interface AddConfigurationProfileParameterRequest {
  parameterName: string;
  expectedValue: string;
  valueType: ParameterValueType;
  required: boolean;
  dynamic: boolean;
  applyAllowed: boolean;
  description?: string;
}

export interface CreateConfigurationCheckJobRequest {
  profileId: number;
  requestedBy: string;
  reason?: string;
}

export interface ConfigurationDriftItemResponse {
  driftItemId: number;
  driftId: number;
  parameterName: string;
  expectedValue: string;
  actualValue: Nullable<string>;
  valueType: ParameterValueType;
  required: boolean;
  dynamic: boolean;
  applyAllowed: boolean;
  complianceStatus: ComplianceStatus;
  message: string;
  createdAt: DateTimeString;
}

export interface ConfigurationDriftResponse {
  driftId: number;
  databaseId: number;
  profileId: number;
  snapshotId: number;
  engineType: DatabaseEngine;
  status: ComplianceStatus;
  totalCount: number;
  compliantCount: number;
  nonCompliantCount: number;
  missingCount: number;
  checkedAt: DateTimeString;
  items: ConfigurationDriftItemResponse[];
}

export interface ConfigurationApplyParameterRequest {
  parameterName: string;
  targetValue: string;
}

export interface CreateConfigurationApplyJobRequest {
  profileId: number;
  requestedBy: string;
  reason?: string;
  parameters: ConfigurationApplyParameterRequest[];
}

export type CreateConfigurationApplyJobResponse = OperationJobResponse;

export interface ConfigurationApplyItemResponse {
  applyItemId: number;
  applyId: number;
  parameterName: string;
  requestedValue: string;
  beforeValue: Nullable<string>;
  afterValue: Nullable<string>;
  valueType: ParameterValueType;
  dynamic: boolean;
  applyAllowed: boolean;
  applyStatus: ConfigurationApplyItemStatus;
  failureCode: Nullable<string>;
  failureMessage: Nullable<string>;
  createdAt: DateTimeString;
  appliedAt: Nullable<DateTimeString>;
  verifiedAt: Nullable<DateTimeString>;
}

export interface ConfigurationApplyResponse {
  applyId: number;
  databaseId: number;
  operationJobId: number;
  requestedBy: string;
  reason: Nullable<string>;
  status: ConfigurationApplyStatus;
  totalCount: number;
  successCount: number;
  failedCount: number;
  skippedCount: number;
  beforeSnapshotId: Nullable<number>;
  afterSnapshotId: Nullable<number>;
  createdAt: DateTimeString;
  startedAt: Nullable<DateTimeString>;
  completedAt: Nullable<DateTimeString>;
  items: ConfigurationApplyItemResponse[];
}