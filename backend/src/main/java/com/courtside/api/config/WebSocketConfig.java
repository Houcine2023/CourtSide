package com.courtside.api.config;

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
                // The Angular dev server runs on another origin; the browser applies
                // the same-origin policy to the WebSocket handshake too.
                .setAllowedOriginPatterns("http://localhost:4200", "http://localhost:*");
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
