package alex.drankov.diary.users.dto;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.entries.dto.UpdateEntryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateRequestTest {
    private final JsonMapper json = JsonMapper.builder()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    @DisplayName("PATCH DTO различает отсутствие поля и явный null")
    void absentAndExplicitNullHaveDifferentMeaning() {
        var request = json.readValue("{\"displayName\":null}", UpdateProfileRequest.class);
        request.validate();
        assertThat(request.hasDisplayName()).isTrue();
        assertThat(request.getDisplayName()).isNull();
        assertThat(request.hasTimeZone()).isFalse();
        assertThat(json.writeValueAsString(request)).doesNotContain("Present");
    }

    @Test
    @DisplayName("Отключение AI через false допустимо, явный null отклоняется")
    void falseAndNullAreNotConfused() {
        var disabled = json.readValue("{\"aiEnabled\":false}", UpdateSettingsRequest.class);
        disabled.validate();
        assertThat(disabled.hasAiEnabled()).isTrue();
        assertThat(disabled.getAiEnabled()).isFalse();
        var missing = json.readValue("{\"aiEnabled\":null}", UpdateSettingsRequest.class);
        assertThatThrownBy(missing::validate).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Интервал напоминаний можно очистить, выход за диапазон отклоняется")
    void nullableRepeatCanBeClearedButOutOfRangeValuesFail() {
        var cleared = json.readValue("{\"reminderRepeatMinutes\":null}", UpdateSettingsRequest.class);
        cleared.validate();
        assertThat(cleared.hasReminderRepeatMinutes()).isTrue();
        assertThat(cleared.getReminderRepeatMinutes()).isNull();
        var invalid = json.readValue("{\"reminderRepeatMinutes\":1441}", UpdateSettingsRequest.class);
        assertThatThrownBy(invalid::validate).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("PATCH DTO отклоняет пустые изменения, неизвестные поля и неверные типы")
    void rejectsEmptyUpdatesUnknownFieldsAndInvalidScalarTypes() {
        assertThatThrownBy(() -> new UpdateProfileRequest().validate()).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new UpdateSettingsRequest().validate()).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new UpdateEntryRequest().validate()).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> json.readValue("{\"unexpected\":true}", UpdateSettingsRequest.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> json.readValue("{\"aiEnabled\":\"true\"}", UpdateSettingsRequest.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> json.readValue("{\"reminderRepeatMinutes\":5.5}", UpdateSettingsRequest.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    @DisplayName("Изменение видимости записи не передаёт замену текста")
    void entryVisibilityDoesNotImplyTextReplacement() {
        var request = json.readValue("{\"reportVisible\":false}", UpdateEntryRequest.class);
        request.validate();
        assertThat(request.hasReportVisible()).isTrue();
        assertThat(request.getReportVisible()).isFalse();
        assertThat(request.hasText()).isFalse();
        var invalid = json.readValue("{\"text\":null}", UpdateEntryRequest.class);
        assertThatThrownBy(invalid::validate).isInstanceOf(ApiException.class);
    }
}
