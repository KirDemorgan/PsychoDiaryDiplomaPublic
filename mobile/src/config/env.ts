const DEFAULT_API_URL = "http://localhost:8080/api/v1";

export const API_BASE_URL = (process.env.EXPO_PUBLIC_API_URL ?? DEFAULT_API_URL).replace(/\/+$/, "");

export const REQUEST_TIMEOUT_MS = 15_000;
