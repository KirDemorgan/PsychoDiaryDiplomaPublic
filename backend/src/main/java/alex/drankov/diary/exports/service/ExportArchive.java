package alex.drankov.diary.exports.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.exports.dto.ExportDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ExportArchive {
    private final JsonMapper json;

    public byte[] create(ExportDocument document) {
        byte[] payload = json.writeValueAsBytes(document);
        if (payload.length > 64 * 1024 * 1024) {
            throw new ApiException(413, "EXPORT_TOO_LARGE", "Экспорт превышает 64 МиБ JSON");
        }
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            entry(zip, "diary.json", payload);
            entry(zip, "README.txt", ("""
                    Дневник состояния: diary-export-v1
                    JSON содержит данные владельца, включая скрытые записи, редакции и историю ревью.
                    Имена таблиц и полей: snake_case, UUID: строки, абсолютное время: ISO-8601 UTC.
                    NULL означает отсутствие данных; шкалы 0–10 независимы. Позиции цитат: UTF-16 [start,end).
                    Расписания сохраняют собственную IANA-зону; плановые дозы вычисляются по версиям.
                    Снимки отчётов содержат JSON в snapshot_json. Устаревшие чувствительные снимки очищены.
                    Пароль, токены, сессии, receipts и сам архив не включены. Импорт пока не поддерживается.
                    Файл содержит личные сведения; получателя выбирает пользователь.
                    """).getBytes(StandardCharsets.UTF_8));
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Export archive generation failed", exception);
        }
    }

    private void entry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }
}
