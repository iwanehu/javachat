package com.chat.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class ChatBackendApplication {
    public static void main(String[] args) {


        SpringApplication app = new SpringApplication(ChatBackendApplication.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.run(args);


    }
}
