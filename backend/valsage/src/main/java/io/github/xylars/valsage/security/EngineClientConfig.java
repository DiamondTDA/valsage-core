package io.github.xylars.valsage.security;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class EngineClientConfig {

    @Bean
    public RestClient engineRestClient(
            @Value("${engine.base-url:http://127.0.0.1:8000}")
            String baseUrl,

            @Value("${engine.connect-timeout:3s}")
            Duration connectTimeout,

            @Value("${engine.read-timeout:30s}")
            Duration readTimeout
    ) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(client);

        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
