package com.chat.client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class LoginController {

    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin;
    @FXML private Label lblError;
    @FXML private Button btnRegistro;

    @FXML
    public void handleLogin(ActionEvent event) {
        String usuario = txtUsuario.getText().trim();
        String password = txtPassword.getText().trim();

        if (usuario.isEmpty() || password.isEmpty()) {
            lblError.setText("Por favor, rellene todos los campos.");
            return;
        }

        lblError.setText("Conectando con el servidor...");
        btnLogin.setDisable(true);

        // Payload JSON manual idéntico a las propiedades del modelo Usuario del Backend
        String jsonPayload = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", usuario, password);

        // Hilo clásico dedicado e independiente para evitar bloqueos del loop de JavaFX
        new Thread(() -> {
            try {
                // Configurar cliente con un tiempo de espera límite de 5 segundos
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/auth/login"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                // Envío de la petición de red
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // Retornar al hilo principal de la UI para procesar la respuesta del Backend
                Platform.runLater(() -> {
                    btnLogin.setDisable(false);
                    if (response.statusCode() == 200) {
                        lblError.setText("¡Acceso correcto!");
                        cargarPantallaChat(usuario);
                    } else if (response.statusCode() == 401) {
                        lblError.setText("Credenciales inválidas.");
                    } else {
                        lblError.setText("Error del servidor (Código: " + response.statusCode() + ").");
                    }
                });

            } catch (Exception e) {
                // Si salta un TimeoutException o un ConnectException, liberamos el botón aquí
                Platform.runLater(() -> {
                    btnLogin.setDisable(false);
                    lblError.setText("Error: No se pudo conectar con el backend.");
                });
                e.printStackTrace();
            }
        }).start();
    }


    @FXML
    public void handleRegistro(ActionEvent event) {
        String usuario = txtUsuario.getText().trim();
        String password = txtPassword.getText().trim();

        if (usuario.isEmpty() || password.isEmpty()) {
            lblError.setText("Introduce usuario y contraseña para registrar.");
            return;
        }

        lblError.setText("Registrando usuario en el servidor...");
        btnRegistro.setDisable(true);

        String jsonPayload = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", usuario, password);

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();


                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/auth/registro"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());


                Platform.runLater(() -> {
                    btnRegistro.setDisable(false);;
                    if (response.statusCode() == 200) {
                        lblError.setText("!Registro completado! Ya puede inicar sesion");
                        lblError.setStyle("-fx-text-fill: #9ece6a;"); //cambia a verde exito
                    }else {
                        lblError.setText("Error: El usuario ya existe o datos invalidos. ");
                        lblError.setStyle("-fx-text-fill: #f7768e;");
                    }
                });



            }catch (Exception e) {
                Platform.runLater(() -> {
                    btnRegistro.setDisable(false);
                    lblError.setText("Error: No se pudo iniciar sesion.");
                });
                e.printStackTrace();
            }
        }).start();
    }

    private void cargarPantallaChat(String usuarioLogueado) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("chat-view.fxml"));
            Parent root = loader.load();

            ChatController chatController = loader.getController();
            chatController.setUsuarioLogueado(usuarioLogueado);

            Scene scene = new Scene(root, 800, 600);
            if (getClass().getResource("styles.css") != null) {
                scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            }

            Stage stage = (Stage) btnLogin.getScene().getWindow();
            stage.setTitle("JavaChat - Sala Principal (" + usuarioLogueado + ")");
            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            lblError.setText("Error crítico al cargar la sala de chat.");
            e.printStackTrace();
        }
    }
}