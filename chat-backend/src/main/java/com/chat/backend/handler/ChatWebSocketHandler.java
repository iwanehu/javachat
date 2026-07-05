package com.chat.backend.handler;

import com.chat.backend.model.Mensaje;
import com.chat.backend.repository.MensajeRepository;
import com.chat.backend.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final MensajeRepository mensajeRepository;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    private static final List<WebSocketSession> sesiones = new CopyOnWriteArrayList<>();
    private static final Map<WebSocketSession, DatosUsuario> mapaUsuarios = new ConcurrentHashMap<>();

    // Usando Lombok @Getter para evitar getters manuales
    @lombok.Getter
    public static class DatosUsuario {
        private final String hash;
        private final String username;
        private final String email;

        public DatosUsuario(String hash, String username, String email) {
            this.hash = hash;
            this.username = username;
            this.email = email;
        }
    }

    @Autowired
    public ChatWebSocketHandler(MensajeRepository mensajeRepository, ObjectMapper objectMapper, JwtUtil jwtUtil) {
        this.mensajeRepository = mensajeRepository;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sesiones.add(session);
        mapaUsuarios.put(session, new DatosUsuario("PENDIENTE_" + session.getId(), "", ""));
        logger.info("Nueva conexión WebSocket: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        String payload = message.getPayload();

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            // --- PROTOCOLO CONNECT_INIT ---
            if (jsonNode.has("type") && "CONNECT_INIT".equals(jsonNode.get("type").asText())) {
                handleConnectInit(session, jsonNode);
                return;
            }

            // --- MENSAJE NORMAL ---
            handleNormalMessage(session, jsonNode);

        } catch (Exception e) {
            logger.error("Error procesando mensaje: {}", e.getMessage(), e);
        }
    }

    private void handleConnectInit(WebSocketSession session, JsonNode jsonNode) throws Exception {
        String token = jsonNode.has("token") ? jsonNode.get("token").asText() : null;
        String username = jsonNode.has("username") ? jsonNode.get("username").asText() : null;

        if (token == null || token.isEmpty() || !jwtUtil.validarToken(token)) {
            logger.warn("Token inválido en CONNECT_INIT para sesión: {}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // Extraer email del token
        String emailUsuario = jwtUtil.extraerUsername(token).trim();

        // Generar hash único para esta sesión
        String hashSesionUnico = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // Guardar datos del usuario
        String displayName = username != null ? username : emailUsuario;
        mapaUsuarios.put(session, new DatosUsuario(hashSesionUnico, displayName, emailUsuario));
        logger.info("Usuario autenticado: {} (email: {}) Hash: {}", displayName, emailUsuario, hashSesionUnico);

        // Confirmación al cliente
        Map<String, Object> respuestaConfirmacion = new ConcurrentHashMap<>();
        respuestaConfirmacion.put("type", "INIT_SUCCESS");
        respuestaConfirmacion.put("hashSesion", hashSesionUnico);
        respuestaConfirmacion.put("username", displayName);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(respuestaConfirmacion)));

        // Notificación de unión
        Map<String, String> avisoUnion = new ConcurrentHashMap<>();
        avisoUnion.put("type", "SISTEMA");
        avisoUnion.put("remitente", "SISTEMA");
        avisoUnion.put("contenido", displayName + " se ha unido al chat.");
        retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(avisoUnion)));

        // Enviar lista actualizada de usuarios
        enviarListaUsuariosActivos();
    }

    private void handleNormalMessage(WebSocketSession session, JsonNode jsonNode) throws Exception {
        DatosUsuario datosRemitente = mapaUsuarios.get(session);
        if (datosRemitente == null || datosRemitente.getHash().startsWith("PENDIENTE_")) {
            logger.warn("Mensaje bloqueado: Sesión no inicializada. Session: {}", session.getId());
            return;
        }

        String contenido = jsonNode.has("contenido") ? jsonNode.get("contenido").asText() : null;
        if (contenido == null || contenido.trim().isEmpty()) {
            return;
        }

        // Guardar mensaje en MongoDB
        Mensaje nuevoMensaje = new Mensaje();
        nuevoMensaje.setRemitente(datosRemitente.getHash());
        nuevoMensaje.setDestinatario("TODOS");
        nuevoMensaje.setContenido(contenido);
        nuevoMensaje.setTimeStamp(LocalDateTime.now());

        // Preparar payload para retransmitir
        Map<String, Object> payloadMensaje = new ConcurrentHashMap<>();
        payloadMensaje.put("type", "MENSAJE");
        payloadMensaje.put("remitente", datosRemitente.getHash());
        payloadMensaje.put("username", datosRemitente.getUsername());
        payloadMensaje.put("contenido", contenido);
        payloadMensaje.put("timeStamp", nuevoMensaje.getTimeStamp().toString());

        // Retransmitir a todos
        retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(payloadMensaje)));

        // Guardar en BD
        try {
            mensajeRepository.save(nuevoMensaje);
        } catch (Exception mongoEx) {
            logger.error("Error guardando en MongoDB: {}", mongoEx.getMessage(), mongoEx);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sesiones.remove(session);
        DatosUsuario datosSaliente = mapaUsuarios.remove(session);

        if (datosSaliente != null && !datosSaliente.getHash().startsWith("PENDIENTE_")) {
            try {
                Map<String, String> avisoSalida = new ConcurrentHashMap<>();
                avisoSalida.put("type", "SISTEMA");
                avisoSalida.put("remitente", "SISTEMA");
                avisoSalida.put("contenido", datosSaliente.getUsername() + " ha abandonado el chat.");

                retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(avisoSalida)));
                enviarListaUsuariosActivos();
            } catch (Exception e) {
                logger.error("Error en salida de usuario: {}", e.getMessage(), e);
            }
        }
    }

    private void retransmitirMensaje(TextMessage message) {
        for (WebSocketSession s : sesiones) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(message);
                } catch (Exception e) {
                    logger.error("Error enviando a sesión {}: {}", s.getId(), e.getMessage());
                }
            }
        }
    }

    private void enviarListaUsuariosActivos() throws Exception {
        Map<String, Object> payloadLista = new ConcurrentHashMap<>();
        payloadLista.put("type", "LISTA_USUARIOS");

        // Crear lista de usuarios activos
        List<Map<String, String>> listaUsuarios = mapaUsuarios.values().stream()
                .filter(du -> !du.getHash().startsWith("PENDIENTE_"))
                .map(du -> {
                    Map<String, String> uMap = new ConcurrentHashMap<>();
                    uMap.put("hash", du.getHash());
                    uMap.put("username", du.getUsername());
                    uMap.put("email", du.getEmail());
                    return uMap;
                })
                .toList();

        payloadLista.put("usuarios", listaUsuarios);
        payloadLista.put("total", listaUsuarios.size());

        retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(payloadLista)));
    }
}