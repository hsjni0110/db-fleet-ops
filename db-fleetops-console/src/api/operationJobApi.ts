import { http } from "./http";
import type {
  CreateBackupJobRequest,
  OperationJobResponse,
} from "../types";

export async function createBackupJob(
  databaseId: number,
  request: CreateBackupJobRequest,
  idempotencyKey?: string,
): Promise<OperationJobResponse> {
  const response = await http.post<OperationJobResponse>(
    `/api/v1/database-instances/${databaseId}/operations/backups`,
    request,
    {
      headers: idempotencyKey
        ? {
            "Idempotency-Key": idempotencyKey,
          }
        : undefined,
    },
  );

  return response.data;
}

export async function getOperationJob(
  jobId: number,
): Promise<OperationJobResponse> {
  const response = await http.get<OperationJobResponse>(
    `/api/v1/jobs/${jobId}`,
  );

  return response.data;
}