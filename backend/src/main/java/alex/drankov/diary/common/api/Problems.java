package alex.drankov.diary.common.api;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;

public final class Problems {
    private Problems() {
    }

    public static ProblemDetail create(
            int status,
            String code,
            String detail,
            HttpServletRequest request
    ) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setTitle(switch (status) {
            case 400 -> "Некорректные данные";
            case 401 -> "Требуется вход";
            case 403 -> "Доступ запрещён";
            case 404 -> "Ресурс не найден";
            case 409 -> "Конфликт данных";
            case 410 -> "Ресурс удалён";
            case 412 -> "Версия изменилась";
            case 428 -> "Требуется версия ресурса";
            case 429 -> "Слишком много запросов";
            default -> "Ошибка обработки запроса";
        });
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("traceId", request.getAttribute("traceId"));
        return problem;
    }
}
