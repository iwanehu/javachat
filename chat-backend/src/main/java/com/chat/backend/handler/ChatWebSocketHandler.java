package com.chat.backend.handler;

import com.chat.backend.model.Mensaje;
import com.chat.backend.repository.MensajeRepository;
import com.chat.backend.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final MensajeRepository mensajeRepository;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    private static final List<WebSocketSession> sesiones = new CopyOnWriteArrayList<>();

    // --- NUEVA ESTRUCTURA INTERNA PARA GUARDAR AMBOS DATOS ---
    public static class DatosUsuario {
        private final String hash;
        private final String username;

        public DatosUsuario(String hash, String username) {
            this.hash = hash;
            this.username = username;
        }

        public String getHash() { return hash; }
        public String getUsername() { return username; }
    }

    // --- CAMBIAMOS EL MAPA PARA QUE GUARDE NUESTRO OBJETO DatosUsuario ---
    private static final Map<WebSocketSession, DatosUsuario> mapaUsuarios = new ConcurrentHashMap<>();

    @Autowired
    public ChatWebSocketHandler(MensajeRepository mensajeRepository, ObjectMapper objectMapper, JwtUtil jwtUtil) {
        this.mensajeRepository = mensajeRepository;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sesiones.add(session);
        // Inicializamos temporalmente con un hash pendiente y username vacío
        mapaUsuarios.put(session, new DatosUsuario("PENDIENTE_" + session.getId(), ""));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            String contenido = jsonNode.has("contenido") ? jsonNode.get("contenido").asText() : null;
            String remitente = jsonNode.has("remitente") ? jsonNode.get("remitente").asText() : null;

            // --- PROTOCOLO CONNECT_INIT ---
            if ("CONNECT_INIT".equals(contenido) && remitente != null) {
                String token = jsonNode.has("token") ? jsonNode.get("token").asText() : null;

                if (token == null || token.isEmpty() || !jwtUtil.validarToken(token)) {
                    System.err.println("Token inválido en CONNECT_INIT.");
                    session.close(CloseStatus.NOT_ACCEPTABLE);
                    return;
                }

                String emailUsuario = jwtUtil.extraerUsername(token).trim();
                String hashSesionUnico = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

                // --- NUEVO: GUARDAMOS EL HASH Y EL USERNAME REAL ---
                mapaUsuarios.put(session, new DatosUsuario(hashSesionUnico, remitente));
                System.out.println("Usuario [" + emailUsuario + "] (" + remitente + ") autenticado. Hash: " + hashSesionUnico);

                // Confirmación al cliente (se queda igual)
                Map<String, Object> respuestaConfirmacion = new ConcurrentHashMap<>();
                respuestaConfirmacion.put("type", "INIT_SUCCESS");
                respuestaConfirmacion.put("hashSesion", hashSesionUnico);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(respuestaConfirmacion)));

                // Notificación al resto usando el nombre real
                Map<String, String> avisoUnion = new ConcurrentHashMap<>();
                avisoUnion.put("remitente", "SISTEMA");
                avisoUnion.put("contenido", remitente + " se ha unido al chat.");
                retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(avisoUnion)));

                enviarListaUsuariosActivos();
                return;
            }

            DatosUsuario datosRemitente = mapaUsuarios.get(session);
            if (datosRemitente == null || datosRemitente.getHash().startsWith("PENDIENTE_")) {
                System.err.println("Mensaje bloqueado: Sesión no inicializada.");
                return;
            }

            Mensaje nuevoMensaje = new Mensaje();
            nuevoMensaje.setRemitente(datosRemitente.getHash());
            nuevoMensaje.setContenido(contenido);
            nuevoMensaje.setTimeStamp(LocalDateTime.now());

            Map<String, Object> payloadMensaje = new ConcurrentHashMap<>();
            payloadMensaje.put("remitente", datosRemitente.getHash());
            payloadMensaje.put("username", datosRemitente.getUsername()); // <--- Nombre real adjunto
            payloadMensaje.put("contenido", contenido);
            payloadMensaje.put("timeStamp", nuevoMensaje.getTimeStamp().toString());

            retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(payloadMensaje)));

            try {
                mensajeRepository.save(nuevoMensaje);
            } catch (Exception mongoEx) {
                System.err.println("Error MongoDB: " + mongoEx.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Error crítico procesando JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sesiones.remove(session);
        DatosUsuario datosSaliente = mapaUsuarios.remove(session);

        if (datosSaliente != null && !datosSaliente.getHash().startsWith("PENDIENTE_")) {
            try {
                Map<String, String> avisoSalida = new ConcurrentHashMap<>();
                avisoSalida.put("remitente", "SISTEMA");
                avisoSalida.put("contenido", datosSaliente.getUsername() + " ha abandonado el chat.");

                retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(avisoSalida)));
                enviarListaUsuariosActivos();
            } catch (Exception e) {
                System.err.println("Error en salida: " + e.getMessage());
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
        payloadLista.put("type", "LISTA_USUARIOS");

        Object[] listaObjetosUsuarios = mapaUsuarios.values().stream()
                .filter(du -> !du.getHash().startsWith("PENDIENTE_"))
                .map(du -> {
                    Map<String, String> uMap = new ConcurrentHashMap<>();
                    uMap.put("hash", du.getHash());
                    uMap.put("username", du.getUsername());
                    return uMap;
                })
                .toArray();

        payloadLista.put("usuarios", listaObjetosUsuarios);
        retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(payloadLista)));
    }
}