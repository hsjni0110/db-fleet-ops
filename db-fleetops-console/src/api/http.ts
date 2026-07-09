import axios, { AxiosError } from "axios";

export interface ProblemDetailResponse {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errorCode?: string;
  requestId?: string;
  timestamp?: string;
  errors?: Array<{
    field: string;
    message: string;
  }>;
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
  },
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    return Promise.reject(error);
  },
);

export function createIdempotencyKey(prefix: string): string {
  const random = crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;

  return `${prefix}-${random}`;
}

export function getProblemDetail(error: unknown): ProblemDetailResponse | null {
  if (!axios.isAxiosError(error)) {
    return null;
  }

  const axiosError = error as AxiosError<ProblemDetailResponse>;

  return axiosError.response?.data ?? null;
}

export function getApiErrorCode(error: unknown): string | null {
  return getProblemDetail(error)?.errorCode ?? null;
}

export function getApiErrorMessage(error: unknown): string {
  const problem = getProblemDetail(error);

  if (problem?.detail) {
    return problem.detail;
  }

  if (problem?.title) {
    return problem.title;
  }

  if (problem?.errors && problem.errors.length > 0) {
    return problem.errors
      .map((item) => `${item.field}: ${item.message}`)
      .join(", ");
  }

  if (error instanceof Error) {
    return error.message;
  }

  return "Unknown error occurred.";
}

export function isDatabaseNotReachableError(error: unknown): boolean {
  return getApiErrorCode(error) === "DBOPS-DATABASE-40001";
}