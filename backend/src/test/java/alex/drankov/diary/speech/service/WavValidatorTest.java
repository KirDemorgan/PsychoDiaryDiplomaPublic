package alex.drankov.diary.speech.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.speech.WavFixtures;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WavValidatorTest {
    private final WavValidator validator = new WavValidator();

    @Test
    @DisplayName("Длительность вычисляется по PCM; принимаются обе границы 0,1–120 секунд")
    void duration() {
        assertThat(validator.validate(WavFixtures.wav(1600))).isEqualTo(100);
        assertThat(validator.validate(WavFixtures.wav(120 * 16000))).isEqualTo(120000);
        assertThatThrownBy(() -> validator.validate(WavFixtures.wav(1599))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate(WavFixtures.wav(120 * 16000 + 1))).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Поддельный WAV, неверный codec, каналы, rate, длины и обрезанные чанки отклоняются")
    void invalidHeaders() {
        assertThatThrownBy(() -> validator.validate(new byte[0])).isInstanceOf(ApiException.class);
        for (int offset : new int[]{0, 4, 8, 16, 20, 22, 24, 28, 32, 34, 40}) {
            var audio = WavFixtures.wav(16000);
            audio[offset] ^= 1;
            assertThatThrownBy(() -> validator.validate(audio)).as("offset %s", offset).isInstanceOf(ApiException.class);
        }
        var audio = WavFixtures.wav(16000);
        ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN).putInt(40, -1);
        assertThatThrownBy(() -> validator.validate(audio)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate(new byte[WavValidator.MAX_BYTES + 1]))
                .isInstanceOfSatisfying(ApiException.class, value -> assertThat(value.getStatus()).isEqualTo(413));
    }
}
