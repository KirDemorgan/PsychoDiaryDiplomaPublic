import type { FieldError, ProblemDetails } from "./types";

export const NETWORK_ERROR = "NETWORK_ERROR";
export const TIMEOUT_ERROR = "TIMEOUT_ERROR";
export const UNEXPECTED_RESPONSE = "UNEXPECTED_RESPONSE";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  readonly traceId?: string;

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = [], traceId?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.traceId = traceId;
  }

  static fromProblem(status: number, problem: ProblemDetails | null): ApiError {
    return new ApiError(
      status,
      problem?.code ?? `HTTP_${status}`,
      problem?.detail ?? problem?.title ?? "Не удалось выполнить запрос",
      problem?.fieldErrors ?? [],
      problem?.traceId,
    );
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isConnectivityProblem(): boolean {
    return this.code === NETWORK_ERROR || this.code === TIMEOUT_ERROR;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function describeError(error: unknown): string {
  if (isApiError(error)) {
    if (error.isConnectivityProblem) {
      return "Нет связи с сервером. Проверьте подключение и попробуйте ещё раз.";
    }
    if (error.status === 429) {
      return "Слишком много попыток. Подождите немного и повторите.";
    }
    if (error.status >= 500) {
      return "Сервер временно недоступен. Попробуйте позже.";
    }
    return error.message;
  }
  return "Что-то пошло не так. Попробуйте ещё раз.";
}
