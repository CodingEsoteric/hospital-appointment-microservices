package edu.assignment.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DownstreamHttpClientFactoryTests {
    @Test
    void jdkHttpClientUsesConfiguredFiniteConnectionTimeout() {
        Duration timeout = Duration.ofMillis(425);

        var client = DownstreamHttpClientFactory.createJdkClient(timeout);

        assertThat(client.connectTimeout()).contains(timeout);
    }
}
