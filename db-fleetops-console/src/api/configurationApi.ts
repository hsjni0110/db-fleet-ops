import { http } from "./http";
import type {
  AddConfigurationProfileParameterRequest,
  ConfigurationApplyResponse,
  ConfigurationDriftResponse,
  ConfigurationProfileParameterResponse,
  ConfigurationProfileResponse,
  CreateConfigurationApplyJobRequest,
  CreateConfigurationApplyJobResponse,
  CreateConfigurationCheckJobRequest,
  CreateConfigurationProfileRequest,
  DatabaseEngine,
} from "../types";

export async function createConfigurationProfile(
  request: CreateConfigurationProfileRequest,
): Promise<ConfigurationProfileResponse> {
  const response = await http.post<ConfigurationProfileResponse>(
    "/api/v1/configuration-profiles",
    request,
  );

  return response.data;
}

export async function getConfigurationProfiles(
  engineType?: DatabaseEngine,
): Promise<ConfigurationProfileResponse[]> {
  const response = await http.get<ConfigurationProfileResponse[]>(
    "/api/v1/configuration-profiles",
    {
      params: {
        engineType,
      },
    },
  );

  return response.data;
}

export async function getConfigurationProfile(
  profileId: number,
): Promise<ConfigurationProfileResponse> {
  const response = await http.get<ConfigurationProfileResponse>(
    `/api/v1/configuration-profiles/${profileId}`,
  );

  return response.data;
}

export async function activateConfigurationProfile(
  profileId: number,
): Promise<ConfigurationProfileResponse> {
  const response = await http.post<ConfigurationProfileResponse>(
    `/api/v1/configuration-profiles/${profileId}/activate`,
  );

  return response.data;
}

export async function deactivateConfigurationProfile(
  profileId: number,
): Promise<ConfigurationProfileResponse> {
  const response = await http.post<ConfigurationProfileResponse>(
    `/api/v1/configuration-profiles/${profileId}/deactivate`,
  );

  return response.data;
}

export async function addConfigurationProfileParameter(
  profileId: number,
  request: AddConfigurationProfileParameterRequest,
): Promise<ConfigurationProfileParameterResponse> {
  const response = await http.post<ConfigurationProfileParameterResponse>(
    `/api/v1/configuration-profiles/${profileId}/parameters`,
    request,
  );

  return response.data;
}

export async function getConfigurationProfileParameters(
  profileId: number,
): Promise<ConfigurationProfileParameterResponse[]> {
  const response = await http.get<ConfigurationProfileParameterResponse[]>(
    `/api/v1/configuration-profiles/${profileId}/parameters`,
  );

  return response.data;
}

export async function createConfigurationCheckJob(
  databaseId: number,
  request: CreateConfigurationCheckJobRequest,
  idempotencyKey?: string,
): Promise<unknown> {
  const response = await http.post(
    `/api/v1/database-instances/${databaseId}/operations/configuration-checks`,
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

export async function getLatestConfigurationDrift(
  databaseId: number,
): Promise<ConfigurationDriftResponse> {
  const response = await http.get<ConfigurationDriftResponse>(
    `/api/v1/database-instances/${databaseId}/configuration-drifts/latest`,
  );

  return response.data;
}

export async function getConfigurationDriftsByDatabase(
  databaseId: number,
): Promise<ConfigurationDriftResponse[]> {
  const response = await http.get<ConfigurationDriftResponse[]>(
    `/api/v1/database-instances/${databaseId}/configuration-drifts`,
  );

  return response.data;
}

export async function getConfigurationDrift(
  driftId: number,
): Promise<ConfigurationDriftResponse> {
  const response = await http.get<ConfigurationDriftResponse>(
    `/api/v1/configuration-drifts/${driftId}`,
  );

  return response.data;
}

export async function createConfigurationApplyJob(
  databaseId: number,
  request: CreateConfigurationApplyJobRequest,
  idempotencyKey?: string,
): Promise<CreateConfigurationApplyJobResponse> {
  const response = await http.post<CreateConfigurationApplyJobResponse>(
    `/api/v1/database-instances/${databaseId}/operations/configuration-applies`,
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

export async function getConfigurationApplyByJobId(
  jobId: number,
): Promise<ConfigurationApplyResponse> {
  const response = await http.get<ConfigurationApplyResponse>(
    `/api/v1/jobs/${jobId}/configuration-apply`,
  );

  return response.data;
}

export async function getConfigurationApply(
  applyId: number,
): Promise<ConfigurationApplyResponse> {
  const response = await http.get<ConfigurationApplyResponse>(
    `/api/v1/configuration-applies/${applyId}`,
  );

  return response.data;
}