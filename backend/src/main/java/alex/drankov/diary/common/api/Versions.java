package alex.drankov.diary.common.api;

public final class Versions {
    private Versions() {
    }

    public static String etag(long version) {
        return "\"v" + version + "\"";
    }

    public static void require(String supplied, long current) {
        if (supplied == null || supplied.isBlank()) {
            throw new ApiException(428, "PRECONDITION_REQUIRED", "Передайте If-Match");
        }
        if (!etag(current).equals(supplied)) {
            throw new ApiException(412, "VERSION_CONFLICT", "Ресурс изменён; получите актуальную версию");
        }
    }
}
