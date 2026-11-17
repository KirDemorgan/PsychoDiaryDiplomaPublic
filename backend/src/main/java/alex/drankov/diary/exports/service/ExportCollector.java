package alex.drankov.diary.exports.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.exports.dto.ExportDocument;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExportCollector {
    private static final int ROW_LIMIT = 10000;
    private final JdbcTemplate jdbc;

    public ExportDocument collect(
            UUID user,
            long revision,
            Instant at
    ) {
        var tables = new LinkedHashMap<String, List<Map<String, Object>>>();
        tables.put("app_user",
                rows("select id, version, created_at, updated_at, email, display_name, time_zone, data_revision from app_user where id=?",
                        user));
        for (String table : List.of("user_settings", "state_rating", "diary_entry", "medication", "intake_event",
                "report")) {
            tables.put(table,
                    rows("select t.* from " + table + " t where t.user_id=? order by t.created_at,t.id", user));
        }
        for (String table : List.of("entry_revision", "tag_review", "entry_tag", "analysis_candidate",
                "review_candidate_decision")) {
            tables.put(table,
                    rows("select t.* from " + table + " t join diary_entry e on e.id=t.entry_id where e.user_id=? order by t.created_at,t.id",
                            user));
        }
        for (String table : List.of("schedule_version", "schedule_slot")) {
            tables.put(table,
                    rows("select t.* from " + table + " t join medication m on m.id=t.medication_id where m.user_id=? order by t.created_at,t.id",
                            user));
        }
        tables.put("ai_task",
                rows("select id,version,created_at,updated_at,user_id,kind,entry_id,report_id,revision,source_data_revision,input_hash,status,attempt,finished_at,model_id,prompt_version,schema_version,error_code from ai_task where user_id=? order by created_at,id",
                        user));
        tables.put("transcription", rows("select id,version,created_at,updated_at,user_id,status,duration_millis,"
                        + "case when expires_at>CURRENT_TIMESTAMP then transcript else null end as transcript,"
                        + "model_id,attempt,expires_at,finished_at,error_code from transcription where user_id=? order by created_at,id",
                user));
        return new ExportDocument("diary-export-v1", at, revision, Collections.unmodifiableMap(tables));
    }

    private List<Map<String, Object>> rows(
            String sql,
            UUID user
    ) {
        var rows = jdbc.queryForList(sql + " limit " + (ROW_LIMIT + 1), user);
        if (rows.size() > ROW_LIMIT) {
            throw new ApiException(413, "EXPORT_TOO_LARGE", "Экспорт превышает ограничение учебной версии");
        }
        return rows.stream().map(row -> {
            var result = new LinkedHashMap<String, Object>();
            row.forEach((key, value) -> result.put(key, normalize(value)));
            return Collections.unmodifiableMap(result);
        }).toList();
    }

    private Object normalize(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof Date date) {
            return date.toLocalDate().toString();
        }
        return value;
    }
}
