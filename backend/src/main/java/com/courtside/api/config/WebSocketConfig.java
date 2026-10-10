package com.courtside.api.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket, so an open booking page sees a slot turn red the instant
 * someone else books it — no polling.
 *
 * Why STOMP and not raw WebSocket? A raw socket is a byte pipe with no notion of
 * "topics" or "subscriptions". STOMP adds those semantics (SUBSCRIBE /topic/x, SEND),
 * which is exactly what a broadcast-to-interested-clients feature needs.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOriginPatterns;

    public WebSocketConfig(
            @Value("${app.websocket.allowed-origins:http://localhost:4200,http://localhost:*}") String allowedOrigins) {
        this.allowedOriginPatterns = parseOrigins(allowedOrigins);
    }

    /**
     * Production runs behind nginx on a real hostname, so the origin the browser
     * sends is that hostname, not localhost. Rather than hard-code the two dev
     * origins forever, they come from `app.websocket.allowed-origins`
     * (env: APP_WEBSOCKET_ALLOWED_ORIGINS, comma-separated): dev keeps its
     * defaults, a deployment wires in its own site origin, and the handshake
     * stays an explicit allowlist — never `*`.
     */
    static String[] parseOrigins(String allowedOrigins) {
        return Arrays.stream((allowedOrigins == null ? "" : allowedOrigins).split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker: fine for one instance. With several instances, a client
        // connected to node A would never receive an event published on node B —
        // that is when you swap this for a relay (RabbitMQ) or Redis pub/sub.
        registry.enableSimpleBroker("/topic");
        // Prefix for messages sent BY clients. We do not accept any today: the server
        // is the only publisher, which removes a whole class of abuse.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // The browser always connects from its own origin: localhost in dev,
                // the deployed host in production (see the constructor).
                .setAllowedOriginPatterns(allowedOriginPatterns);
        // Plain WebSocket, not .withSockJS().

        // SockJS and the client disagreed: the server spoke the SockJS protocol
        // while stompjs opened a raw WebSocket to this same path, and SockJS replied
        // to that handshake with HTTP 400. Live availability updates therefore never
        // arrived, and the page silently fell back to never updating.
        //
        // SockJS exists to reach browsers that cannot do WebSocket at all. Every
        // browser this app supports can, so the fallback was carrying a real cost
        // (a second transport to keep in sync) and buying nothing. A native
        // endpoint keeps dev and production on exactly the same handshake.
    }
}
