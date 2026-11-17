package alex.drankov.diary.ai.adapter;



public interface ModelAdapter {
    String modelId();

    boolean available();

    String generate(ModelRequest request) throws Exception;
}
