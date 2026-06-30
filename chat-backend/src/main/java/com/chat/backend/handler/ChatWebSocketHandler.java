package com.chat.backend.handler;

import com.chat.backend.model.Mensaje;
import com.chat.backend.repository.MensajeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private ObjectMapper objectMapper; // Jackson para transformar el JSON crudo en objeto Java

    // Lista de sesiones activas
    private static final List<WebSocketSession> sesiones = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sesiones.add(session);
        System.out.println("Nueva sesión WebSocket conectada: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Mensaje recibido: " + payload);

        try {
            // 1. Mapear el JSON crudo que manda JavaFX al objeto Mensaje de MongoDB
            Mensaje nuevoMensaje = objectMapper.readValue(payload, Mensaje.class);

            // 2. Si el mensaje no trae marca de tiempo o destinatario, se asignan por defecto
            if (nuevoMensaje.getTimeStamp() == null) {
                nuevoMensaje.setTimeStamp(LocalDateTime.now());
            }
            if (nuevoMensaje.getDestinatario() == null) {
                nuevoMensaje.setDestinatario("TODOS");
            }

            // 3. 💾 GUARDAR EN MONGODB
            mensajeRepository.save(nuevoMensaje);
            System.out.println("Mensaje persistido en MongoDB con ID: " + nuevoMensaje.getId());

        } catch (Exception e) {
            System.err.println("Error al parsear o persistir el mensaje: " + e.getMessage());
        }

        // 4. Retransmitir el mensaje recibido (el JSON crudo) a todos los conectados
        for (WebSocketSession s : sesiones) {
            if (s.isOpen()) {
                s.sendMessage(message);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sesiones.remove(session);
        System.out.println("Sesión WebSocket cerrada: " + session.getId());
    }
}