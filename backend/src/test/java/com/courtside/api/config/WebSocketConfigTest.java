package com.courtside.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

class WebSocketConfigTest {

    @Test
    void parsesCommaSeparatedOriginsAndTrimsBlanks() {
        assertThat(WebSocketConfig.parseOrigins("http://localhost:4200, https://courtside.example.com ,"))
                .containsExactly("http://localhost:4200", "https://courtside.example.com");
    }

    @Test
    void blankOrNullValueYieldsNoPatterns() {
        assertThat(WebSocketConfig.parseOrigins("   ,  ")).isEmpty();
        assertThat(WebSocketConfig.parseOrigins("")).isEmpty();
        assertThat(WebSocketConfig.parseOrigins(null)).isEmpty();
    }

    @Test
    void registersEndpointWithConfiguredOrigins() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);

        new WebSocketConfig("https://a.example, https://b.example").registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(registration).setAllowedOriginPatterns("https://a.example", "https://b.example");
    }

    @Test
    void defaultsToLocalDevOrigins() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);

        new WebSocketConfig("http://localhost:4200,http://localhost:*").registerStompEndpoints(registry);

        verify(registration).setAllowedOriginPatterns("http://localhost:4200", "http://localhost:*");
    }
}