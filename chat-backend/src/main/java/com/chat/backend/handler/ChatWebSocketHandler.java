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
    private ObjectMapper objectMapper;

    // Lista de sesiones activas globales
    private static final List<WebSocketSession> sesiones = new CopyOnWriteArrayList<>();

    // Mapa concurrente para rastrear el nombre de usuario de cada sesión activa
    private static final Map<WebSocketSession, String> mapaUsuarios = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 🟢 Recuperamos el nombre de usuario inyectado de forma segura por el interceptor JWT
        String nombreUsuario = (String) session.getAttributes().get("username");

        if (nombreUsuario == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // Eliminamos sesiones colgadas o previas del mismo usuario (mitigación estricta de duplicados)
        for (Map.Entry<WebSocketSession, String> entry : mapaUsuarios.entrySet()) {
            if (entry.getValue().equals(nombreUsuario)) {
                WebSocketSession sesionVieja = entry.getKey();
                sesiones.remove(sesionVieja);
                mapaUsuarios.remove(sesionVieja);
                if (sesionVieja.isOpen()) {
                    try { sesionVieja.close(); } catch (Exception e) {}
                }
            }
        }

        // Registramos la nueva sesión verificada
        sesiones.add(session);
        mapaUsuarios.put(session, nombreUsuario);
        System.out.println("Sesión WebSocket autenticada conectada: " + nombreUsuario + " (" + session.getId() + ")");

        // 🟢 El servidor genera autónomamente el mensaje de unión del sistema
        Map<String, String> avisoUnion = new ConcurrentHashMap<>();
        avisoUnion.put("remitente", "SISTEMA");
        avisoUnion.put("contenido", nombreUsuario + " se ha unido al chat.");
        String jsonUnion = objectMapper.writeValueAsString(avisoUnion);

        retransmitirMensaje(new TextMessage(jsonUnion));

        // Enviamos la lista actualizada a todos
        enviarListaUsuariosActivos();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // 🟢 Obtenemos el usuario real de la sesión (así evitamos que alteren el "remitente" desde la consola del navegador)
        String usuarioAutenticado = mapaUsuarios.get(session);

        if (usuarioAutenticado == null) {
            System.err.println("Mensaje rechazado: Sesión no asociada a ningún usuario válido.");
            return;
        }

        try {
            Mensaje nuevoMensaje = objectMapper.readValue(payload, Mensaje.class);

            // 🟢 Sobreescritura de seguridad con los datos reales del JWT
            nuevoMensaje.setRemitente(usuarioAutenticado);

            if (nuevoMensaje.getTimeStamp() == null) {
                nuevoMensaje.setTimeStamp(LocalDateTime.now());
            }
            if (nuevoMensaje.getDestinatario() == null) {
                nuevoMensaje.setDestinatario("TODOS");
            }

            // Guardar en MongoDB
            mensajeRepository.save(nuevoMensaje);
            System.out.println("Mensaje de " + usuarioAutenticado + " persistido en MongoDB.");

            // Retransmitimos el mensaje sanitizado y verificado por el servidor
            String jsonMensajeVerificado = objectMapper.writeValueAsString(nuevoMensaje);
            retransmitirMensaje(new TextMessage(jsonMensajeVerificado));

        } catch (Exception e) {
            System.err.println("Error al parsear o persistir el mensaje: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sesiones.remove(session);
        String usuarioSaliente = mapaUsuarios.remove(session);
        System.out.println("Sesión WebSocket cerrada: " + session.getId() + " perteneciente a: " + usuarioSaliente);

        if (usuarioSaliente != null) {
            try {
                Map<String, String> avisoSalida = new ConcurrentHashMap<>();
                avisoSalida.put("remitente", "SISTEMA");
                avisoSalida.put("contenido", usuarioSaliente + " ha salido del chat.");
                String jsonSalida = objectMapper.writeValueAsString(avisoSalida);

                retransmitirMensaje(new TextMessage(jsonSalida));
                enviarListaUsuariosActivos();
            } catch (Exception e) {
                System.err.println("Error al gestionar salida de usuario: " + e.getMessage());
            }
        }
    }

    private void retransmitirMensaje(TextMessage message) throws Exception {
        for (WebSocketSession s : sesiones) {
            if (s.isOpen()) {
                s.sendMessage(message);
            }
        }
    }

    private void enviarListaUsuariosActivos() throws Exception {
        Map<String, Object> payloadLista = new ConcurrentHashMap<>();
        // Nota: Cambiado a LISTA_USUARIOS para mantener sincronía exacta con el frontend original
        payloadLista.put("type", "LISTA_USUARIOS");

        // Obtenemos solo los nombres únicos por seguridad
        String[] usuariosUnicos = mapaUsuarios.values().stream().distinct().toArray(String[]::new);
        payloadLista.put("usuarios", usuariosUnicos);

        String jsonLista = objectMapper.writeValueAsString(payloadLista);
        TextMessage textMessageLista = new TextMessage(jsonLista);

        for (WebSocketSession s : sesiones) {
            if (s.isOpen()) {
                s.sendMessage(textMessageLista);
            }
        }
    }
}