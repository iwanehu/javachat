package com.chat.backend.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        // Render y Cloudflare a veces exigen que si el cliente envió un Sec-WebSocket-Protocol,
        // este se devuelva intacto en la respuesta HTTP 101, si no el navegador aborta.
        List<String> protocols = request.getHeaders().get("Sec-WebSocket-Protocol");
        if (protocols != null && !protocols.isEmpty()) {
            response.getHeaders().set("Sec-WebSocket-Protocol", protocols.get(0));
        }

        // Devolvemos SIEMPRE true para delegar el control de identidad por completo
        // al ChatWebSocketHandler mediante el mensaje inicial 'CONNECT_INIT'
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}