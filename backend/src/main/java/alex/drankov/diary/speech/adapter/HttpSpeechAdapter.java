package alex.drankov.diary.speech.adapter;

import alex.drankov.diary.common.http.BoundedResponseBody;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class HttpSpeechAdapter implements SpeechAdapter {
    private final URI endpoint;
    private final String model;
    private final String token;
    private final Duration timeout;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    public HttpSpeechAdapter(
            @Value("${app.speech.endpoint:}")
            String endpoint,
            @Value("${app.speech.model-id:}")
            String model,
            @Value("${app.speech.token:}")
            String token,
            @Value("${app.speech.timeout:PT60S}")
            Duration timeout
    ) {
        this.endpoint = endpoint.isBlank() ? null : URI.create(endpoint);
        this.model = model.isBlank() ? null : model;
        this.token = token;
        this.timeout = timeout;
        if (this.endpoint != null && (!Set.of("http", "https").contains(this.endpoint.getScheme())
                || this.endpoint.getHost() == null || this.endpoint.getUserInfo() != null
                || this.endpoint.getQuery() != null || this.endpoint.getFragment() != null)) {
            throw new IllegalArgumentException("Invalid speech endpoint");
        }
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Invalid speech timeout");
        }
        if ((this.endpoint == null) != (this.model == null) || model.length() > 255) {
            throw new IllegalArgumentException("Speech endpoint and model-id must be configured together");
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
    public String transcribe(byte[] wav) throws Exception {
        if (!available()) {
            throw new IllegalStateException("Speech model is not configured");
        }
        var builder = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "audio/wav")
                .header("Accept", "application/json").header("X-Speech-Model", model)
                .header("X-Speech-Language", "ru").POST(HttpRequest.BodyPublishers.ofByteArray(wav));
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        var response = client.send(builder.build(), ignored -> new BoundedResponseBody(65536));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Speech service failed");
        }
        try (var parser = json.createParser(response.body())) {
            var root = json.readTree(parser);
            if (root == null || !root.isObject() || root.size() != 2 || !root.has("text") || !root.has("modelId")
                    || !root.get("text").isString() || !root.get("modelId").isString()
                    || !model.equals(root.get("modelId").asString()) || parser.nextToken() != null) {
                throw new IllegalArgumentException("Invalid speech response");
            }
            return root.get("text").asString();
        }
    }
}
