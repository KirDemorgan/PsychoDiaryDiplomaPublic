package alex.drankov.diary.config;

import alex.drankov.diary.entries.dto.CreateEntryRequest;
import alex.drankov.diary.ratings.dto.PutRatingRequest;
import alex.drankov.diary.users.dto.UpdateSettingsRequest;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    @Test
    @DisplayName("PUT оценки требует три явных поля, принимает NULL и сохраняет ноль")
    void ratingRequiresPresenceAndAllowsNull() {
        context.run(application -> {
            var json = application.getBean(JsonMapper.class);
            var request = json.readValue("{\"anxiety\":0,\"mood\":null,\"energy\":10,\"timeZone\":\"UTC\"}", PutRatingRequest.class);
            assertThat(request.anxiety()).isZero();
            assertThat(request.mood()).isNull();
            assertThat(request.energy()).isEqualTo(10);
            assertThatThrownBy(() -> json.readValue("{\"anxiety\":0,\"energy\":10,\"timeZone\":\"UTC\"}", PutRatingRequest.class))
                    .isInstanceOf(JacksonException.class);
        });
    }

    @Test
    @DisplayName("Настройки Jackson загружаются и сохраняют смещение входной даты")
    void applicationConfigurationBindsAndPreservesIncomingOffset() {
        context.run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(JsonMapper.class);
            var json = application.getBean(JsonMapper.class);
            assertThat(json.isEnabled(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)).isFalse();
            assertThat(json.readValue("\"2026-10-13T16:00:00+05:00\"", OffsetDateTime.class))
                    .isEqualTo(OffsetDateTime.parse("2026-10-13T16:00:00+05:00"));
            var request = json.readValue("""
                    {
                        "text": "Тестовая запись",
                        "occurredAt": "2026-10-13T16:00:00+05:00",
                        "timeZone": "Asia/Yekaterinburg"
                    }
                    """, CreateEntryRequest.class);
            assertThat(request.occurredAt().getOffset().getTotalSeconds()).isEqualTo(18000);
        });
    }

    @Test
    @DisplayName("Настройки Jackson запрещают неизвестные поля и преобразование неверных типов")
    void applicationConfigurationRejectsUnknownFieldsAndScalarCoercion() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            var json = application.getBean(JsonMapper.class);
            assertThatThrownBy(() -> json.readValue("{\"unknown\":true}", UpdateSettingsRequest.class))
                    .isInstanceOf(JacksonException.class);
            assertThatThrownBy(() -> json.readValue("{\"aiEnabled\":\"true\"}", UpdateSettingsRequest.class))
                    .isInstanceOf(JacksonException.class);
            assertThatThrownBy(() -> json.readValue("{\"reminderRepeatMinutes\":5.5}", UpdateSettingsRequest.class))
                    .isInstanceOf(JacksonException.class);
        });
    }
}
