package alex.drankov.diary.common.api;

import alex.drankov.diary.auth.service.AuthService;
import alex.drankov.diary.entries.service.EntryService;
import alex.drankov.diary.ratings.dto.PutRatingRequest;
import alex.drankov.diary.ratings.service.RatingService;
import alex.drankov.diary.users.service.ProfileService;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RulesTest {
    @Test
    @DisplayName("Изменение требует точного сильного ETag актуальной версии")
    void versionsRequireExactStrongEtag() {
        assertThat(Versions.etag(3)).isEqualTo("\"v3\"");
        Versions.require("\"v3\"", 3);
        assertThatThrownBy(() -> Versions.require(null, 3)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(428));
        assertThatThrownBy(() -> Versions.require("", 3)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Versions.require("W/\"v3\"", 3)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(412));
        assertThatThrownBy(() -> Versions.require("\"v2\"", 3)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Длина записи учитывает Unicode-символы, а не кодовые единицы UTF-16")
    void textUsesCodePointsNotUtf16() {
        EntryService.validateText("😀".repeat(5000));
        assertThatThrownBy(() -> EntryService.validateText("😀".repeat(5001))).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest(name = "{displayName}: вариант {index}, текст = [{0}]")
    @DisplayName("Пустой или отсутствующий текст записи отклоняется")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    void rejectsEmptyText(String text) {
        assertThatThrownBy(() -> EntryService.validateText(text)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Ноль сохраняется как оценка, null означает отсутствие наблюдения")
    void zeroIsAnObservationNullIsMissing() {
        RatingService.validateValues(new PutRatingRequest(0, null, 10, "UTC"));
        assertThatThrownBy(
                () -> RatingService.validateValues(new PutRatingRequest(null, null, null, "UTC"))).isInstanceOf(
                ApiException.class);
        assertThatThrownBy(() -> RatingService.validateValues(new PutRatingRequest(-1, 0, 0, "UTC"))).isInstanceOf(
                ApiException.class);
        assertThatThrownBy(() -> RatingService.validateValues(new PutRatingRequest(0, 11, 0, "UTC"))).isInstanceOf(
                ApiException.class);
    }

    @Test
    @DisplayName("Смещение времени события должно соответствовать указанной зоне")
    void localTimeOffsetMustMatchZone() {
        TimeRules.occurrence(OffsetDateTime.parse("2026-10-13T15:00:00+05:00"), "Asia/Yekaterinburg");
        assertThatThrownBy(() -> TimeRules.occurrence(OffsetDateTime.parse("2026-10-13T15:00:00Z"),
                "Asia/Yekaterinburg")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> TimeRules.occurrence(null, "UTC")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> TimeRules.zone("+05:00")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> TimeRules.zone(null)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Период включает граничные даты и ограничивает допустимую длину")
    void dateRangesAreInclusiveAndBounded() {
        var date = LocalDate.of(2026, 1, 1);
        TimeRules.range(date, date.plusDays(365), 366);
        assertThatThrownBy(() -> TimeRules.range(date, date.plusDays(366), 366)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> TimeRules.range(date, date.minusDays(1), 366)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> TimeRules.range(null, date, 366)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> TimeRules.range(date, null, 366)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Пароли длиннее 72 UTF-8 байт отклоняются без усечения")
    void bcryptDoesNotSilentlyTruncateMultibytePasswords() {
        AuthService.validatePassword("я".repeat(36));
        assertThatThrownBy(() -> AuthService.validatePassword("я".repeat(37))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> AuthService.validatePassword(null)).isInstanceOf(ApiException.class);
        assertThat(AuthService.normalize(" Demo@EXAMPLE.TEST ")).isEqualTo("demo@example.test");
    }

    @Test
    @DisplayName("Необязательное имя можно очистить")
    void optionalNameCanBeCleared() {
        ProfileService.validateName(null);
        ProfileService.validateName("😀".repeat(100));
        assertThatThrownBy(() -> ProfileService.validateName(" ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ProfileService.validateName("x".repeat(101))).isInstanceOf(ApiException.class);
    }
}
