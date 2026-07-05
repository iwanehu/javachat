package com.chat.backend.controller;

import com.chat.backend.model.Mensaje;
import com.chat.backend.repository.MensajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/mensajes")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MensajeController {

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @GetMapping("/publicos")
    public List<Mensaje> obtenerMensajesPublico() {
        try {
            System.out.println("📡 Obteniendo mensajes públicos...");
            Query query = new Query();
            query.with(Sort.by(Sort.Direction.ASC, "timeStamp"));
            List<Mensaje> mensajes = mongoTemplate.find(query, Mensaje.class);
            System.out.println("📜 Mensajes públicos encontrados: " + mensajes.size());
            return mensajes;
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo mensajes públicos: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @GetMapping("/historial")
    public List<Mensaje> obtenerHistorial(@RequestParam(defaultValue = "30") int limite) {
        try {
            System.out.println("📡 Solicitando historial con límite: " + limite);

            // Usar MongoTemplate directamente con consulta simple
            Query query = new Query();
            query.with(Sort.by(Sort.Direction.DESC, "timeStamp"));
            query.limit(limite);

            List<Mensaje> mensajes = mongoTemplate.find(query, Mensaje.class);

            if (mensajes == null || mensajes.isEmpty()) {
                System.out.println("📭 No hay mensajes en la base de datos");
                return Collections.emptyList();
            }

            // Revertir para mostrar del más antiguo al más nuevo
            Collections.reverse(mensajes);
            System.out.println("📜 Historial encontrado: " + mensajes.size() + " mensajes");

            // Log de los primeros 3 mensajes para debug
            for (int i = 0; i < Math.min(3, mensajes.size()); i++) {
                System.out.println("  - " + mensajes.get(i).getRemitente() + ": " + mensajes.get(i).getContenido());
            }

            return mensajes;
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo historial: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @GetMapping("/historial/usuario/{hash}")
    public List<Mensaje> obtenerHistorialPorUsuario(@PathVariable String hash, @RequestParam(defaultValue = "30") int limite) {
        try {
            System.out.println("📡 Solicitando historial para usuario: " + hash);

            Query query = new Query();
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("remitente").is(hash));
            query.with(Sort.by(Sort.Direction.DESC, "timeStamp"));
            query.limit(limite);

            List<Mensaje> mensajes = mongoTemplate.find(query, Mensaje.class);

            if (mensajes == null || mensajes.isEmpty()) {
                System.out.println("📭 No hay mensajes para el usuario: " + hash);
                return Collections.emptyList();
            }

            Collections.reverse(mensajes);
            System.out.println("📜 Historial usuario encontrado: " + mensajes.size() + " mensajes");

            return mensajes;
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo historial por usuario: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @GetMapping("/count")
    public String contarMensajes() {
        try {
            long count = mensajeRepository.count();
            System.out.println("📊 Total de mensajes en MongoDB: " + count);
            return "Total de mensajes en MongoDB: " + count;
        } catch (Exception e) {
            System.err.println("❌ Error contando mensajes: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/test")
    public String testConexion() {
        try {
            System.out.println("🔄 Probando conexión a MongoDB...");
            long count = mensajeRepository.count();
            System.out.println("✅ Conexión exitosa. Mensajes: " + count);
            return "✅ MongoDB conectado correctamente. Mensajes: " + count;
        } catch (Exception e) {
            System.err.println("❌ Error de conexión: " + e.getMessage());
            e.printStackTrace();
            return "❌ Error: " + e.getMessage();
        }
    }

    @PostMapping("/guardar")
    public Mensaje guardarMensaje(@RequestBody Mensaje mensaje) {
        try {
            if (mensaje.getTimeStamp() == null) {
                mensaje.setTimeStamp(LocalDateTime.now());
            }
            if (mensaje.getDestinatario() == null || mensaje.getDestinatario().isEmpty()) {
                mensaje.setDestinatario("TODOS");
            }

            System.out.println("💾 Guardando mensaje:");
            System.out.println("  - Remitente: " + mensaje.getRemitente());
            System.out.println("  - Destinatario: " + mensaje.getDestinatario());
            System.out.println("  - Contenido: " + mensaje.getContenido());

            Mensaje saved = mensajeRepository.save(mensaje);
            System.out.println("✅ Mensaje guardado con ID: " + saved.getId());

            return saved;
        } catch (Exception e) {
            System.err.println("❌ Error guardando mensaje: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @PostMapping("/crear-prueba")
    public String crearMensajePrueba() {
        try {
            System.out.println("🔄 Creando mensaje de prueba...");

            Mensaje msg1 = new Mensaje();
            msg1.setRemitente("SISTEMA");
            msg1.setDestinatario("TODOS");
            msg1.setContenido("📢 Mensaje de prueba 1 - Chat iniciado");
            msg1.setTimeStamp(LocalDateTime.now());
            mensajeRepository.save(msg1);

            Mensaje msg2 = new Mensaje();
            msg2.setRemitente("usr_test");
            msg2.setDestinatario("TODOS");
            msg2.setContenido("👋 Mensaje de prueba 2 - Hola mundo");
            msg2.setTimeStamp(LocalDateTime.now());
            mensajeRepository.save(msg2);

            System.out.println("✅ Mensajes de prueba creados");
            return "✅ Mensajes de prueba creados correctamente";
        } catch (Exception e) {
            System.err.println("❌ Error creando mensajes de prueba: " + e.getMessage());
            e.printStackTrace();
            return "❌ Error: " + e.getMessage();
        }
    }
}