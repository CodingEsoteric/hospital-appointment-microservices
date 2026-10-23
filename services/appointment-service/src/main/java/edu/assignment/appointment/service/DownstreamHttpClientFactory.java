package edu.assignment.appointment.service;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class DownstreamHttpClientFactory {
    private DownstreamHttpClientFactory() {}

    static RestClient create(String baseUrl, Duration connectTimeout, Duration responseTimeout) {
        HttpClient httpClient = createJdkClient(connectTimeout);
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(responseTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    static HttpClient createJdkClient(Duration connectTimeout) {
        return HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }
}
