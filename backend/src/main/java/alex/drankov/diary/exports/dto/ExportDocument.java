package alex.drankov.diary.exports.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExportDocument(
        String formatVersion,
        Instant exportedAt,
        long sourceDataRevision,
        Map<String, List<Map<String, Object>>> tables
) {
    @Override
    public String toString() {
        return "ExportDocument[formatVersion=" + formatVersion + "]";
    }
}
