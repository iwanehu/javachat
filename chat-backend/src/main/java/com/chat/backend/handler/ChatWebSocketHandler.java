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
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private ObjectMapper objectMapper; // Jackson para transformar el JSON crudo en objeto Java

    // Lista de sesiones activas
    private static final List<WebSocketSession> sesiones = new CopyOnWriteArrayList<>();

    // Mapa concurrente para rastrear el nombre de usuario de cada sesión activa
    private static final Map<WebSocketSession, String> mapaUsuarios = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!sesiones.contains(session)) {
            sesiones.add(session);
        }
        System.out.println("Nueva sesión WebSocket conectada: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Mensaje recibido: " + payload);

        try {
            Mensaje nuevoMensaje = objectMapper.readValue(payload, Mensaje.class);

            if (nuevoMensaje.getTimeStamp() == null) {
                nuevoMensaje.setTimeStamp(LocalDateTime.now());
            }
            if (nuevoMensaje.getDestinatario() == null) {
                nuevoMensaje.setDestinatario("TODOS");
            }

            // Interceptamos usando el remitente original del mensaje de unión del SISTEMA
            if ("SISTEMA".equals(nuevoMensaje.getRemitente()) && nuevoMensaje.getContenido().contains("se ha unido al chat.")) {
                String nombreUsuario = nuevoMensaje.getContenido().split(" ")[0].trim();

                // Si este usuario ya tenía una sesión colgada vieja por el StrictMode de React, la cerramos y limpiamos
                mapaUsuarios.entrySet().removeIf(entry -> {
                    if (entry.getValue().equals(nombreUsuario) && !entry.getKey().getId().equals(session.getId())) {
                        try {
                            sesiones.remove(entry.getKey());
                            if (entry.getKey().isOpen()) {
                                entry.getKey().close();
                            }
                        } catch (Exception e) {
                            // Sesión vieja ya cerrada
                        }
                        return true;
                    }
                    return false;
                });

                // Registramos la sesión limpia actual en memoria
                mapaUsuarios.put(session, nombreUsuario);
                System.out.println("Usuario registrado en memoria: " + nombreUsuario);

                // Retransmitimos el aviso de unión a los chats
                retransmitirMensaje(message);

                // Forzamos el envío de la lista actualizada y limpia
                enviarListaUsuariosActivos();
                return;
            }

            // Guardar en MongoDB mensajes normales
            mensajeRepository.save(nuevoMensaje);
            System.out.println("Mensaje persistido en MongoDB con ID: " + nuevoMensaje.getId());

        } catch (Exception e) {
            System.err.println("Error al parsear o persistir el mensaje: " + e.getMessage());
        }

        retransmitirMensaje(message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sesiones.remove(session);
        String usuarioSaliente = mapaUsuarios.remove(session);
        System.out.println("Sesión WebSocket cerrada: " + session.getId());

        if (usuarioSaliente != null) {
            try {
                // Creamos un mensaje simulado del sistema para notificar la salida en el feed
                Map<String, String> avisoSalida = new ConcurrentHashMap<>();
                avisoSalida.put("remitente", "SISTEMA");
                avisoSalida.put("contenido", usuarioSaliente + " ha salido del chat.");
                String jsonSalida = objectMapper.writeValueAsString(avisoSalida);

                retransmitirMensaje(new TextMessage(jsonSalida));

                // Actualizamos la lista de la barra lateral para todos los que quedan
                enviarListaUsuariosActivos();
            } catch (Exception e) {
                System.err.println("Error al gestionar salida de usuario: " + e.getMessage());
            }
        }
    }

    // Auxiliar para evitar duplicar el bucle for de retransmisión
    private void retransmitirMensaje(TextMessage message) throws Exception {
        for (WebSocketSession s : sesiones) {
            if (s.isOpen()) {
                s.sendMessage(message);
            }
        }
    }

    // Genera el JSON especial de control y lo distribuye
    private void enviarListaUsuariosActivos() throws Exception {
        Map<String, Object> payloadLista = new ConcurrentHashMap<>();
        payloadLista.put("type", "LISTA_USUARIOS");

        // Convertimos los valores a un array nativo para evitar problemas de serialización con Jackson
        payloadLista.put("usuarios", mapaUsuarios.values().toArray(new String[0]));

        String jsonLista = objectMapper.writeValueAsString(payloadLista);
        System.out.println("Enviando lista de usuarios: " + jsonLista);

        TextMessage textMessageLista = new TextMessage(jsonLista);

        for (WebSocketSession s : sesiones) {
            if (s.isOpen()) {
                s.sendMessage(textMessageLista);
            }
        }
    }
}