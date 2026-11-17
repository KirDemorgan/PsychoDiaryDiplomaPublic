import { ApiError, NETWORK_ERROR, TIMEOUT_ERROR, describeError, isApiError } from "./errors";

describe("ApiError", () => {
  it("собирается из problem+json", () => {
    const error = ApiError.fromProblem(400, {
      code: "VALIDATION_ERROR",
      detail: "Проверьте поля запроса",
      traceId: "t-1",
      fieldErrors: [{ field: "email", code: "INVALID", message: "Некорректное значение" }],
    });

    expect(error.status).toBe(400);
    expect(error.code).toBe("VALIDATION_ERROR");
    expect(error.message).toBe("Проверьте поля запроса");
    expect(error.traceId).toBe("t-1");
    expect(error.fieldErrors).toHaveLength(1);
  });

  it("подставляет значения по умолчанию без тела ответа", () => {
    const error = ApiError.fromProblem(502, null);

    expect(error.code).toBe("HTTP_502");
    expect(error.message).toBe("Не удалось выполнить запрос");
    expect(error.fieldErrors).toEqual([]);
  });

  it("использует title, если detail отсутствует", () => {
    expect(ApiError.fromProblem(409, { title: "Конфликт" }).message).toBe("Конфликт");
  });

  it("распознаёт 401 и проблемы связи", () => {
    expect(new ApiError(401, "X", "m").isUnauthorized).toBe(true);
    expect(new ApiError(0, NETWORK_ERROR, "m").isConnectivityProblem).toBe(true);
    expect(new ApiError(0, TIMEOUT_ERROR, "m").isConnectivityProblem).toBe(true);
    expect(new ApiError(400, "X", "m").isConnectivityProblem).toBe(false);
  });

  it("isApiError отличает ApiError от прочих ошибок", () => {
    expect(isApiError(new ApiError(400, "X", "m"))).toBe(true);
    expect(isApiError(new Error("m"))).toBe(false);
  });
});

describe("describeError", () => {
  it.each([
    ["связь", new ApiError(0, NETWORK_ERROR, "m"), "Нет связи с сервером. Проверьте подключение и попробуйте ещё раз."],
    ["лимит", new ApiError(429, "X", "m"), "Слишком много попыток. Подождите немного и повторите."],
    ["сервер", new ApiError(503, "X", "m"), "Сервер временно недоступен. Попробуйте позже."],
    ["доменная ошибка", new ApiError(401, "INVALID_CREDENTIALS", "Неверные данные входа"), "Неверные данные входа"],
    ["неизвестная", new Error("boom"), "Что-то пошло не так. Попробуйте ещё раз."],
  ])("%s", (_name, error, expected) => {
    expect(describeError(error)).toBe(expected);
  });
});
