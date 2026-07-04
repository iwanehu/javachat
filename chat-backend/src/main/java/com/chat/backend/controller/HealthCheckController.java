package com.chat.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

    // Captura pings HEAD y GET dirigidos a la raíz que no sean WebSockets
    @RequestMapping(value = "/", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Backend funcionando correctamente");
    }
}