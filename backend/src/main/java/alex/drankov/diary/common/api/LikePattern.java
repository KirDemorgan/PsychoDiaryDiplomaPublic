package alex.drankov.diary.common.api;

import java.util.Locale;

public final class LikePattern {
    private LikePattern() {
    }

    public static String contains(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
