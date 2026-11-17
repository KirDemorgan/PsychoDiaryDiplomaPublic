package alex.drankov.diary.speech.service;

import alex.drankov.diary.common.api.ApiException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

@Component
public class WavValidator {
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    public long validate(byte[] audio) {
        if (audio.length > MAX_BYTES) {
            throw new ApiException(413, "AUDIO_TOO_LARGE", "Аудио превышает 4 MiB");
        }
        if (audio.length < 44 || !tag(audio, 0).equals("RIFF") || !tag(audio, 8).equals("WAVE")) {
            throw invalid();
        }
        var buffer = ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN);
        if (Integer.toUnsignedLong(buffer.getInt(4)) != audio.length - 8L) {
            throw invalid();
        }
        boolean format = false;
        long samples = -1;
        int offset = 12;
        while (offset + 8 <= audio.length) {
            String type = tag(audio, offset);
            long size = Integer.toUnsignedLong(buffer.getInt(offset + 4));
            long end = offset + 8L + size;
            if (end + (size & 1) > audio.length) {
                throw invalid();
            }
            int start = offset + 8;
            if (type.equals("fmt ")) {
                if (format || size != 16 || buffer.getShort(start) != 1 || buffer.getShort(start + 2) != 1
                        || buffer.getInt(start + 4) != 16000 || buffer.getInt(start + 8) != 32000
                        || buffer.getShort(start + 12) != 2 || buffer.getShort(start + 14) != 16) {
                    throw invalid();
                }
                format = true;
            } else if (type.equals("data")) {
                if (!format || samples >= 0 || size == 0 || (size & 1) != 0) {
                    throw invalid();
                }
                samples = size / 2;
            }
            offset = (int) (end + (size & 1));
        }
        if (offset != audio.length || !format || samples < 1600 || samples > 120L * 16000) {
            throw invalid();
        }
        return samples * 1000 / 16000;
    }

    private String tag(byte[] audio, int offset) {
        return new String(audio, offset, 4, StandardCharsets.US_ASCII);
    }

    private ApiException invalid() {
        return new ApiException(415, "UNSUPPORTED_AUDIO", "Ожидается WAV PCM16 mono 16 kHz длительностью 0,1–120 секунд");
    }
}
