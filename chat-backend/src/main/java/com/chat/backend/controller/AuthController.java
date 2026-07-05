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
    public ResponseEntity<Map<String, String>> registrar(@RequestBody Usuario usuario) {
        Map<String, String> response = new HashMap<>();

        // 1. Verificar si el usuario ya existe
        if (usuarioRepository.findByUsername(usuario.getUsername()).isPresent()) {
            response.put("message", "El usuario ya existe.");
            response.put("success", "false");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String password = usuario.getPassword();

        // 2. Validación: Mínimo 8 caracteres
        if (password == null || password.length() < 8) {
            response.put("message", "La contraseña debe tener un mínimo de 8 caracteres.");
            response.put("success", "false");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Guardamos el usuario
        usuarioRepository.save(usuario);
        response.put("message", "Usuario registrado correctamente.");
        response.put("success", "true");
        response.put("username", usuario.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Usuario usuario) {
        Map<String, String> response = new HashMap<>();
        Optional<Usuario> userOpt = usuarioRepository.findByUsername(usuario.getUsername());

        if (userOpt.isPresent() && userOpt.get().getPassword().equals(usuario.getPassword())) {
            // Generamos el token legítimo firmado de 24h
            String token = jwtUtil.generarToken(usuario.getUsername());

            response.put("token", token);
            response.put("username", usuario.getUsername());
            response.put("success", "true");
            return ResponseEntity.ok(response);
        }

        response.put("message", "Credenciales inválidas");
        response.put("success", "false");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
}