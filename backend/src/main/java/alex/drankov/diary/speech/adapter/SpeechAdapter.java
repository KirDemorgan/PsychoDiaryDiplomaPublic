package alex.drankov.diary.speech.adapter;

public interface SpeechAdapter {
    String modelId();

    boolean available();

    String transcribe(byte[] wav) throws Exception;
}
