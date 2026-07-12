package com.bowon.cpm.common.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.time.Duration;

@Configuration
public class WebClientConfig {

    /** 일반 API 버퍼 크기: 2MB */
    private static final int DEFAULT_BUFFER_SIZE = 2 * 1024 * 1024;

    /**
     * DART corpCode.xml ZIP 버퍼 크기: 10MB
     * corpCode.xml ZIP은 약 2~3MB이므로 여유 있게 설정
     */
    private static final int DART_BUFFER_SIZE = 10 * 1024 * 1024;

    @Bean
    public WebClient kisWebClient(
            WebClient.Builder builder,
            @Value("${external.kis.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .clientConnector(connector())
                .exchangeStrategies(exchangeStrategies(DEFAULT_BUFFER_SIZE))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient dartWebClient(
            WebClient.Builder builder,
            @Value("${external.dart.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .clientConnector(dartConnector())
                // DART corpCode.xml ZIP 파일이 크므로 버퍼 크기를 10MB로 설정
                .exchangeStrategies(exchangeStrategies(DART_BUFFER_SIZE))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient naverWebClient(
            WebClient.Builder builder,
            @Value("${external.naver.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .clientConnector(connector())
                .exchangeStrategies(exchangeStrategies(DEFAULT_BUFFER_SIZE))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient openAiWebClient(
            WebClient.Builder builder,
            @Value("${external.openai.base-url}") String baseUrl
    ) {
        return builder
                .baseUrl(baseUrl)
                .clientConnector(connector())
                .exchangeStrategies(exchangeStrategies(DEFAULT_BUFFER_SIZE))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * WebClient 버퍼 크기 설정
     * 기본값(256KB)으로는 DART corpCode.xml 같은 큰 파일 수신 불가
     *
     * @param maxBufferSize 최대 버퍼 크기 (bytes)
     */
    private ExchangeStrategies exchangeStrategies(int maxBufferSize) {
        return ExchangeStrategies.builder()
                .codecs((ClientCodecConfigurer configurer) ->
                        configurer.defaultCodecs().maxInMemorySize(maxBufferSize))
                .build();
    }

    private ReactorClientHttpConnector connector() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30)); // DART ZIP 다운로드 시간 고려해 30초로 늘림

        return new ReactorClientHttpConnector(httpClient);
    }

    private ReactorClientHttpConnector dartConnector() {
        HttpClient httpClient = HttpClient.create()
                .protocol(HttpProtocol.HTTP11)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30))
                .secure(ssl -> ssl.sslContext(dartSslContext()));

        return new ReactorClientHttpConnector(httpClient);
    }

    private SslContext dartSslContext() {
        try {
            return SslContextBuilder.forClient()
                    .protocols("TLSv1.2")
                    .build();
        } catch (SSLException e) {
            throw new IllegalStateException("Failed to create DART SSL context", e);
        }
    }
}

