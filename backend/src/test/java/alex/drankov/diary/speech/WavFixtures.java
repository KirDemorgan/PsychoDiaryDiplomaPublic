package alex.drankov.diary.speech;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class WavFixtures {
    private WavFixtures() {
    }

    public static byte[] wav(int samples) {
        var buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + samples * 2)
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples * 2);
        return buffer.array();
    }
}
