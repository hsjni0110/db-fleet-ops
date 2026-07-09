import { http } from "./http";
import type {
  CreateDatabaseInstanceRequest,
  DatabaseInstanceResponse,
  DatabaseInstanceSummary,
  DefaultDatabaseHealthResponse,
  InventoryHealthCheckResponse,
  UpdateDatabaseInstanceRequest,
} from "../types";

export async function createDatabaseInstance(
  request: CreateDatabaseInstanceRequest,
): Promise<DatabaseInstanceResponse> {
  const response = await http.post<DatabaseInstanceResponse>(
    "/api/v1/database-instances",
    request,
  );

  return response.data;
}

export async function getDatabaseInstances(): Promise<DatabaseInstanceSummary[]> {
  const response = await http.get<DatabaseInstanceSummary[]>(
    "/api/v1/database-instances",
  );

  return response.data;
}

export async function getDatabaseInstance(
  databaseId: number,
): Promise<DatabaseInstanceResponse> {
  const response = await http.get<DatabaseInstanceResponse>(
    `/api/v1/database-instances/${databaseId}`,
  );

  return response.data;
}

export async function updateDatabaseInstance(
  databaseId: number,
  request: UpdateDatabaseInstanceRequest,
): Promise<DatabaseInstanceResponse> {
  const response = await http.patch<DatabaseInstanceResponse>(
    `/api/v1/database-instances/${databaseId}`,
    request,
  );

  return response.data;
}

export async function deactivateDatabaseInstance(
  databaseId: number,
): Promise<void> {
  await http.delete(`/api/v1/database-instances/${databaseId}`);
}

export async function getDefaultDatabaseHealth(): Promise<DefaultDatabaseHealthResponse> {
  const response = await http.get<DefaultDatabaseHealthResponse>(
    "/api/v1/databases/default/health",
  );

  return response.data;
}

export async function runDatabaseHealthCheck(
  databaseId: number,
): Promise<InventoryHealthCheckResponse> {
  const response = await http.post<InventoryHealthCheckResponse>(
    `/api/v1/database-instances/${databaseId}/health-checks`,
  );

  return response.data;
}