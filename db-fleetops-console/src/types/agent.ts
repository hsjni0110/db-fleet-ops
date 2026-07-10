import type { DateTimeString, Nullable } from "./common";
import type { OperationTaskType, OperationTaskStatus } from "./operationJob";

export type AgentStatus = "ONLINE" | "OFFLINE" | "UNKNOWN" | "DISABLED";

export interface AgentRegisterRequest {
  agentName: string;
  hostname: string;
  ipAddress: string;
  osName: string;
  architecture: string;
  agentVersion: string;
}

export interface AgentRegisterResponse {
  agentId: number;
  agentToken: string;
  status: AgentStatus;
}

export interface AgentHeartbeatRequest {
  agentToken: string;
  cpuUsagePercent: number;
  memoryUsagePercent: number;
  diskUsagePercent: number;
}

export interface AgentHeartbeatResponse {
  agentId: number;
  status: AgentStatus;
  lastHeartbeatAt: DateTimeString;
}

export interface AgentTaskResponse {
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

export interface NextAgentTaskResponse {
  hasTask: boolean;
  taskId: Nullable<number>;
  taskType: Nullable<OperationTaskType>;
  parametersJson: Nullable<string>;
}

export interface StartAgentTaskRequest {
  agentToken: string;
}

export interface CompleteAgentTaskRequest {
  agentToken: string;
  resultPayloadJson: string;
}

export interface FailAgentTaskRequest {
  agentToken: string;
  errorCode: string;
  errorMessage: string;
}

export interface AgentConsoleResponse {
  agentId: number;
  agentName: string;
  hostname: string;
  ipAddress: string;
  osName: string;
  architecture: string;
  agentVersion: string;
  status: AgentStatus;
  lastHeartbeatAt?: Nullable<DateTimeString>;
  heartbeatDelaySeconds?: Nullable<number>;
  createdAt: DateTimeString;
  updatedAt: DateTimeString;
}

export interface AgentHostMetricResponse {
  metricId: number;
  agentId: number;
  cpuUsagePercent: number;
  memoryUsagePercent: number;
  diskUsagePercent: number;
  collectedAt: DateTimeString;
}

export interface AgentDetailResponse {
  agent: AgentConsoleResponse;
  recentHostMetrics: AgentHostMetricResponse[];
  recentOperationTasks: AgentTaskResponse[];
}
