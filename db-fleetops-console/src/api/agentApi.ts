import { http } from "./http";
import type {
  AgentHeartbeatRequest,
  AgentHeartbeatResponse,
  AgentConsoleResponse,
  AgentDetailResponse,
  AgentRegisterRequest,
  AgentRegisterResponse,
  AgentTaskResponse,
  CompleteAgentTaskRequest,
  FailAgentTaskRequest,
  NextAgentTaskResponse,
  StartAgentTaskRequest,
} from "../types";

export async function getAgents(): Promise<AgentConsoleResponse[]> {
  const response = await http.get<AgentConsoleResponse[]>("/api/v1/agents");

  return response.data;
}

export async function getAgentDetail(agentId: number): Promise<AgentDetailResponse> {
  const response = await http.get<AgentDetailResponse>(`/api/v1/agents/${agentId}`);

  return response.data;
}

export async function registerAgent(
  request: AgentRegisterRequest,
): Promise<AgentRegisterResponse> {
  const response = await http.post<AgentRegisterResponse>(
    "/internal/v1/agents/register",
    request,
  );

  return response.data;
}

export async function sendAgentHeartbeat(
  agentId: number,
  request: AgentHeartbeatRequest,
): Promise<AgentHeartbeatResponse> {
  const response = await http.post<AgentHeartbeatResponse>(
    `/internal/v1/agents/${agentId}/heartbeats`,
    request,
  );

  return response.data;
}

export async function getNextAgentTask(
  agentId: number,
  agentToken: string,
): Promise<NextAgentTaskResponse> {
  const response = await http.get<NextAgentTaskResponse>(
    `/internal/v1/agents/${agentId}/tasks/next`,
    {
      params: {
        agentToken,
      },
    },
  );

  return response.data;
}

export async function startAgentTask(
  agentId: number,
  taskId: number,
  request: StartAgentTaskRequest,
): Promise<AgentTaskResponse> {
  const response = await http.post<AgentTaskResponse>(
    `/internal/v1/agents/${agentId}/tasks/${taskId}/start`,
    request,
  );

  return response.data;
}

export async function completeAgentTask(
  agentId: number,
  taskId: number,
  request: CompleteAgentTaskRequest,
): Promise<AgentTaskResponse> {
  const response = await http.post<AgentTaskResponse>(
    `/internal/v1/agents/${agentId}/tasks/${taskId}/complete`,
    request,
  );

  return response.data;
}

export async function failAgentTask(
  agentId: number,
  taskId: number,
  request: FailAgentTaskRequest,
): Promise<AgentTaskResponse> {
  const response = await http.post<AgentTaskResponse>(
    `/internal/v1/agents/${agentId}/tasks/${taskId}/fail`,
    request,
  );

  return response.data;
}
