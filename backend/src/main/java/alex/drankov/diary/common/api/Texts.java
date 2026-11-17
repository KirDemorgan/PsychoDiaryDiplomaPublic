package alex.drankov.diary.common.api;

public final class Texts {
    private Texts() {
    }

    public static boolean exceeds(
            String value,
            int maxCodePoints
    ) {
        return value.codePointCount(0, value.length()) > maxCodePoints;
    }
}
