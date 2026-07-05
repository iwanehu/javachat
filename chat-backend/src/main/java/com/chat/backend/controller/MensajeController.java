package com.chat.backend.controller;

import com.chat.backend.model.Mensaje;
import com.chat.backend.repository.MensajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;

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
        return mensajeRepository.findByDestinatarioOrderByTimeStampAsc("TODOS");
    }

    @GetMapping("/historial")
    public List<Mensaje> obtenerHistorial(@RequestParam(defaultValue = "30") int limite) {
        try {
            System.out.println("📡 Solicitando historial con límite: " + limite);

            // Usar el repositorio directamente
            List<Mensaje> mensajes = mensajeRepository.findTop30ByOrderByTimeStampDesc();

            if (mensajes == null || mensajes.isEmpty()) {
                System.out.println("📭 No hay mensajes en la base de datos");
                return Collections.emptyList();
            }

            // Revertir el orden para mostrar del más antiguo al más nuevo
            Collections.reverse(mensajes);
            System.out.println("📜 Historial encontrado: " + mensajes.size() + " mensajes");

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

            List<Mensaje> mensajes = mensajeRepository.findTop30ByRemitenteOrderByTimeStampDesc(hash);

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

    @PostMapping("/guardar")
    public Mensaje guardarMensaje(@RequestBody Mensaje mensaje) {
        try {
            System.out.println("💾 Guardando mensaje: " + mensaje.getContenido());
            return mensajeRepository.save(mensaje);
        } catch (Exception e) {
            System.err.println("❌ Error guardando mensaje: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}