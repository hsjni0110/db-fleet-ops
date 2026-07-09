export type Nullable<T> = T | null;

export type DatabaseEngine = "MYSQL" | "POSTGRESQL";

export type DatabaseEnvironment =
  | "LOCAL"
  | "DEV"
  | "STAGING"
  | "PRODUCTION"
  | string;

export type DateTimeString = string;

export interface ApiErrorResponse {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errorCode?: string;
  timestamp?: string;
}

export interface UnsupportedFeature {
  supported: false;
  reason: string;
}