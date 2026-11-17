package alex.drankov.diary.common.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadSize(Exception exception, HttpServletRequest request) {
        return Problems.create(413, "AUDIO_TOO_LARGE", "Превышен допустимый размер загрузки", request);
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    ProblemDetail multipart(Exception exception, HttpServletRequest request) {
        return Problems.create(400, "INVALID_MULTIPART", "Проверьте multipart-запрос и поле audio", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail mediaType(Exception exception, HttpServletRequest request) {
        return Problems.create(415, "UNSUPPORTED_MEDIA_TYPE", "Неподдерживаемый тип запроса", request);
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail domain(ApiException exception, HttpServletRequest request) {
        return Problems.create(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var problem = Problems.create(400, "VALIDATION_ERROR", "Проверьте поля запроса", request);
        problem.setProperty("fieldErrors", exception.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "code", "INVALID", "message", "Некорректное значение"))
                .distinct().toList());
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ProblemDetail badRequest(Exception exception, HttpServletRequest request) {
        return Problems.create(400, "VALIDATION_ERROR", "Проверьте формат и поля запроса", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail header(MissingRequestHeaderException exception, HttpServletRequest request) {
        boolean version = "If-Match".equalsIgnoreCase(exception.getHeaderName());
        return Problems.create(version ? 428 : 400, version ? "PRECONDITION_REQUIRED" : "VALIDATION_ERROR",
                version ? "Передайте If-Match" : "Отсутствует обязательный заголовок", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail stale(Exception exception, HttpServletRequest request) {
        return Problems.create(412, "VERSION_CONFLICT", "Ресурс изменён; получите актуальную версию", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(Exception exception, HttpServletRequest request) {
        return Problems.create(409, "DATA_CONFLICT", "Операция конфликтует с существующими данными", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(Exception exception, HttpServletRequest request) {
        return Problems.create(403, "FORBIDDEN", "Действие запрещено", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail missing(Exception exception, HttpServletRequest request) {
        return Problems.create(404, "RESOURCE_NOT_FOUND", "Ресурс не найден", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
        // Never include request bodies, exception messages, SQL or tokens in client errors.
        return Problems.create(500, "INTERNAL_ERROR", "Не удалось обработать запрос", request);
    }
}
