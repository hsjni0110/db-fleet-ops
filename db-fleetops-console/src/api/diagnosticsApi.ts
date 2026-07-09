import { http } from "./http";
import type {
  ConnectionSummaryResponse,
  DatabaseSessionResponse,
  DatabaseUptimeResponse,
  DatabaseVersionResponse,
  LockWaitResponse,
  LongTransactionResponse,
  SlowQueryResponse,
} from "../types";

const diagnosticsBasePath = (databaseId: number) => {
  return `/api/v1/database-instances/${databaseId}/diagnostics`;
};

export async function getDatabaseVersion(
  databaseId: number,
): Promise<DatabaseVersionResponse> {
  const response = await http.get<DatabaseVersionResponse>(
    `${diagnosticsBasePath(databaseId)}/version`,
  );

  return response.data;
}

export async function getDatabaseUptime(
  databaseId: number,
): Promise<DatabaseUptimeResponse> {
  const response = await http.get<DatabaseUptimeResponse>(
    `${diagnosticsBasePath(databaseId)}/uptime`,
  );

  return response.data;
}

export async function getConnectionSummary(
  databaseId: number,
): Promise<ConnectionSummaryResponse> {
  const response = await http.get<ConnectionSummaryResponse>(
    `${diagnosticsBasePath(databaseId)}/connections`,
  );

  return response.data;
}

export async function getDatabaseSessions(
  databaseId: number,
): Promise<DatabaseSessionResponse[]> {
  const response = await http.get<DatabaseSessionResponse[]>(
    `${diagnosticsBasePath(databaseId)}/sessions`,
  );

  return response.data;
}

export async function getLongTransactions(
  databaseId: number,
): Promise<LongTransactionResponse[]> {
  const response = await http.get<LongTransactionResponse[]>(
    `${diagnosticsBasePath(databaseId)}/long-transactions`,
  );

  return response.data;
}

export async function getLockWaits(
  databaseId: number,
): Promise<LockWaitResponse[]> {
  const response = await http.get<LockWaitResponse[]>(
    `${diagnosticsBasePath(databaseId)}/lock-waits`,
  );

  return response.data;
}

export async function getSlowQueries(
  databaseId: number,
): Promise<SlowQueryResponse[]> {
  const response = await http.get<SlowQueryResponse[]>(
    `${diagnosticsBasePath(databaseId)}/slow-queries`,
  );

  return response.data;
}