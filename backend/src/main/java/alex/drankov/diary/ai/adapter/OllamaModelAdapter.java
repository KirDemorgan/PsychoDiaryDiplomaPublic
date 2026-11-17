package alex.drankov.diary.ai.adapter;

import alex.drankov.diary.ai.entity.AiKind;
import alex.drankov.diary.common.http.BoundedResponseBody;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class OllamaModelAdapter implements ModelAdapter, AutoCloseable {
    private final URI endpoint;
    private final String model;
    private final String token;
    private final Duration timeout;
    private final int context;
    private final int predict;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final JsonNode extractionSchema = schema("entry-candidates-v1");
    private final JsonNode overviewSchema = schema("report-narrative-v1");

    public OllamaModelAdapter(String endpoint, String model, String token, Duration timeout, int context, int predict) {
        this.endpoint = endpoint.isBlank() ? null : URI.create(endpoint);
        this.model = model.isBlank() ? null : model;
        this.token = token;
        this.timeout = timeout;
        this.context = context;
        this.predict = predict;
        if (this.endpoint != null && (!Set.of("http", "https").contains(this.endpoint.getScheme())
                || this.endpoint.getHost() == null || this.endpoint.getUserInfo() != null
                || this.endpoint.getQuery() != null || this.endpoint.getFragment() != null)) {
            throw new IllegalArgumentException("Invalid Ollama endpoint");
        }
        if ((this.endpoint == null) != (this.model == null) || model.length() > 255) {
            throw new IllegalArgumentException("Ollama endpoint and model-id must be configured together");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(5)) > 0
                || context < 1024 || context > 131072 || predict < 256 || predict > context) {
            throw new IllegalArgumentException("Invalid Ollama limits");
        }
    }

    @Override
    public String modelId() {
        return model;
    }

    @Override
    public boolean available() {
        return endpoint != null;
    }

    @Override
    public String generate(ModelRequest request) throws Exception {
        if (!available()) {
            throw new IllegalStateException("Text model is not configured");
        }
        JsonNode schema;
        if (request.kind() == AiKind.ENTRY && "entry-candidates-v1".equals(request.schemaVersion())) {
            schema = extractionSchema;
        } else if (request.kind() == AiKind.REPORT && "report-narrative-v1".equals(request.schemaVersion())) {
            schema = overviewSchema;
        } else {
            throw new IllegalArgumentException("Unsupported model schema");
        }
        byte[] payload = json.writeValueAsBytes(Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.inputJson())),
                "format", schema, "stream", false, "think", false,
                "options", Map.of("temperature", 0, "num_ctx", context, "num_predict", predict)));
        if (payload.length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Model input exceeds limit");
        }
        var builder = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json")
                .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(payload));
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        var response = client.send(builder.build(), ignored -> new BoundedResponseBody(256 * 1024));
        if (response.statusCode() != 200) {
            throw new IOException("Text inference service failed");
        }
        try (var parser = json.createParser(response.body())) {
            var result = json.readTree(parser);
            if (result == null || !result.isObject() || result.has("error")
                    || !result.hasNonNull("model") || !result.get("model").isString()
                    || !model.equals(result.get("model").asString())
                    || !result.hasNonNull("done") || !result.get("done").isBoolean() || !result.get("done").asBoolean()
                    || !result.hasNonNull("done_reason") || !"stop".equals(result.get("done_reason").asString())
                    || !result.hasNonNull("message") || !result.get("message").isObject()
                    || !result.get("message").hasNonNull("role") || !"assistant".equals(
                    result.get("message").get("role").asString())
                    || result.get("message").hasNonNull("tool_calls")
                    || !result.get("message").hasNonNull("content") || !result.get("message").get("content").isString()
                    || parser.nextToken() != null) {
                throw new IllegalArgumentException("Invalid Ollama response");
            }
            String content = result.get("message").get("content").asString();
            if (content.isBlank() || content.length() > 65536) {
                throw new IllegalArgumentException("Invalid text model result size");
            }
            return content;
        }
    }

    private JsonNode schema(String version) {
        try (var stream = OllamaModelAdapter.class.getResourceAsStream("/ai/schemas/" + version + ".json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing model schema");
            }
            return json.readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load model schema", exception);
        }
    }

    @Override
    public void close() {
        client.shutdownNow();
    }
}
