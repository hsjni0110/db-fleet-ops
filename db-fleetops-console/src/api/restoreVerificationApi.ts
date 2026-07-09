import { http } from "./http";
import type { RestoreVerificationResponse } from "../types";

export async function getRestoreVerificationByJobId(
  jobId: number,
): Promise<RestoreVerificationResponse> {
  const response = await http.get<RestoreVerificationResponse>(
    `/api/v1/jobs/${jobId}/restore-verification`,
  );

  return response.data;
}

export async function getLatestRestoreVerificationByDatabaseId(
  databaseId: number,
): Promise<RestoreVerificationResponse> {
  const response = await http.get<RestoreVerificationResponse>(
    `/api/v1/databases/${databaseId}/restore-verifications/latest`,
  );

  return response.data;
}

export async function getRestoreVerification(
  verificationId: number,
): Promise<RestoreVerificationResponse> {
  const response = await http.get<RestoreVerificationResponse>(
    `/api/v1/restore-verifications/${verificationId}`,
  );

  return response.data;
}