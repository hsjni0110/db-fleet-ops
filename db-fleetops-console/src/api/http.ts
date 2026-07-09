import axios from "axios";

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