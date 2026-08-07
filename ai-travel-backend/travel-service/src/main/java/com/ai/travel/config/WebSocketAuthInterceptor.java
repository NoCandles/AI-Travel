package com.ai.travel.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Authenticates a WebSocket handshake before the session is created.
 *
 * <p>The mini program sends its login JWT in the {@code token} query parameter.
 * A direct client may instead use the {@code X-Token} header. Client-provided
 * user IDs are deliberately not accepted as authentication.</p>
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = extractToken(request);
        String userId = token == null ? null : jwtUtil.getUserIdFromToken(token);

        if (userId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            log.warn("WebSocket handshake rejected: path={}, tokenPresent={}",
                    request.getURI().getPath(), token != null);
            return false;
        }

        attributes.put("userId", userId);
        log.debug("WebSocket handshake authenticated: path={}, userId={}",
                request.getURI().getPath(), userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.warn("WebSocket handshake failed: path={}, reason={}",
                    request.getURI().getPath(), exception.getMessage());
        }
    }

    private String extractToken(ServerHttpRequest request) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        String headerToken = request.getHeaders().getFirst("X-Token");
        return headerToken == null || headerToken.isBlank() ? null : headerToken;
    }
}
