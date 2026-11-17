export interface UserResponse {
  id: string;
  email: string;
  displayName: string | null;
  timeZone: string;
  version: number;
  dataRevision: number;
}

export interface AuthResponse {
  user: UserResponse;
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName?: string;
  timeZone: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export interface ProblemDetails {
  status?: number;
  code?: string;
  detail?: string;
  title?: string;
  traceId?: string;
  fieldErrors?: FieldError[];
}
