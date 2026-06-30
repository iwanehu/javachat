package com.chat.backend.controller;


import com.chat.backend.model.Usuario;
import com.chat.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;


    @PostMapping("/registro")
    public ResponseEntity<String> registrar(@RequestBody Usuario usuario){
        if(usuarioRepository.findByUsername(usuario.getUsername()).isPresent()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El ussuario ya existe");
        }
        usuarioRepository.save(usuario);
        return ResponseEntity.ok("usuario registrado correctamente");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody Usuario usuario){
        Optional<Usuario> userOpt = usuarioRepository.findByUsername(usuario.getUsername());

        if (userOpt.isPresent() && userOpt.get().getPassword().equals(usuario.getPassword())){
            return ResponseEntity.ok("Login correcto");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales invalidos");
    }

}
