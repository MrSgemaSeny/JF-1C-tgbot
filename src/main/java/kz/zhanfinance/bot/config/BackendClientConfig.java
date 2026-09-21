package kz.zhanfinance.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class BackendClientConfig {

    @Value("${jf.backend.base-url:http://localhost:8080/api}")
    private String baseUrl;

    @Value("${jf.backend.internal-token:}")
    private String internalToken;

    @Value("${jf.backend.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${jf.backend.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public RestClient backendRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Token", internalToken != null ? internalToken : "")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }
}
