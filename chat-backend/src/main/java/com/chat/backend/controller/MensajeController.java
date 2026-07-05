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
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class MensajeController {

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @GetMapping("/publicos")
    @CrossOrigin(origins = "*")
    public List<Mensaje> obtenerMensajesPublico() {
        return mensajeRepository.findByDestinatarioOrderByTimeStampAsc("TODOS");
    }

    @GetMapping("/historial")
    @CrossOrigin(origins = "*")
    public List<Mensaje> obtenerHistorial(@RequestParam(defaultValue = "30") int limite) {
        try {
            // Obtener los últimos 'limite' mensajes ordenados por timestamp descendente
            Query query = new Query();
            query.with(Sort.by(Sort.Direction.DESC, "timeStamp"));
            query.limit(limite);

            List<Mensaje> mensajes = mongoTemplate.find(query, Mensaje.class);

            // Revertir el orden para mostrar del más antiguo al más nuevo
            Collections.reverse(mensajes);

            return mensajes;
        } catch (Exception e) {
            System.err.println("Error obteniendo historial: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @GetMapping("/historial/usuario/{hash}")
    @CrossOrigin(origins = "*")
    public List<Mensaje> obtenerHistorialPorUsuario(@PathVariable String hash, @RequestParam(defaultValue = "30") int limite) {
        try {
            Query query = new Query();
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("remitente").is(hash));
            query.with(Sort.by(Sort.Direction.DESC, "timeStamp"));
            query.limit(limite);

            List<Mensaje> mensajes = mongoTemplate.find(query, Mensaje.class);
            Collections.reverse(mensajes);

            return mensajes;
        } catch (Exception e) {
            System.err.println("Error obteniendo historial por usuario: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @PostMapping("/guardar")
    @CrossOrigin(origins = "*")
    public Mensaje guardarMensaje(@RequestBody Mensaje mensaje) {
        return mensajeRepository.save(mensaje);
    }
}