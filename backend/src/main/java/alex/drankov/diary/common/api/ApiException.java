package alex.drankov.diary.common.api;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(
            int status,
            String code,
            String message
    ) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound() {
        return new ApiException(404, "RESOURCE_NOT_FOUND", "Ресурс не найден");
    }

    public static ApiException conflict(
            String code,
            String message
    ) {
        return new ApiException(409, code, message);
    }

    public static ApiException invalid(String message) {
        return new ApiException(400, "VALIDATION_ERROR", message);
    }
}
