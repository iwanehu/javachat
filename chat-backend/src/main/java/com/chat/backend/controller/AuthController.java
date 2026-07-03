package com.chat.backend.controller;


import com.chat.backend.model.Usuario;
import com.chat.backend.repository.UsuarioRepository;
import com.chat.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtUtil jwtUtil;


    @PostMapping("/registro")
    public ResponseEntity<String> registrar(@RequestBody Usuario usuario) {
        // 1. Verificar si el usuario ya existe
        if (usuarioRepository.findByUsername(usuario.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El usuario ya existe.");
        }

        String password = usuario.getPassword();

        // 2. Validación: Mínimo 8 caracteres (Acepta espacios y caracteres especiales de forma nativa)
        if (password == null || password.length() < 8) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("La contraseña debe tener un mínimo de 8 caracteres.");
        }

        // Guardamos directamente si cumple con la longitud
        usuarioRepository.save(usuario);
        return ResponseEntity.ok("Usuario registrado correctamente.");
    }




    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Usuario usuario){
        Optional<Usuario> userOpt = usuarioRepository.findByUsername(usuario.getUsername());

        if (userOpt.isPresent() && userOpt.get().getPassword().equals(usuario.getPassword())){
            //  Generamos el token legítimo firmado de 24h
            String token = jwtUtil.generarToken(usuario.getUsername());

            // Enviamos un JSON estructurado que el frontend procesará limpiamente
            Map<String, String> respuesta = new HashMap<>();
            respuesta.put("token", token);
            respuesta.put("username", usuario.getUsername());

            return ResponseEntity.ok(respuesta);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales inválidas");
    }

}
