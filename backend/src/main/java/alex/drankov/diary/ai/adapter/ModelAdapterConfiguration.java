package alex.drankov.diary.ai.adapter;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ModelAdapterConfiguration {
    @Bean
    @ConditionalOnMissingBean(ModelAdapter.class)
    OllamaModelAdapter modelAdapter(
            @Value("${app.ai.ollama.endpoint:}")
            String endpoint,
            @Value("${app.ai.ollama.model-id:}")
            String model,
            @Value("${app.ai.ollama.token:}")
            String token,
            @Value("${app.ai.timeout:PT30S}")
            Duration timeout,
            @Value("${app.ai.ollama.num-context:16384}")
            int context,
            @Value("${app.ai.ollama.num-predict:8192}")
            int predict
    ) {
        return new OllamaModelAdapter(endpoint, model, token, timeout, context, predict);
    }
}
