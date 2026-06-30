package com.chat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Launcher extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // Cargaremos la vista inicial de Login
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("login.fxml"));


        if (getClass().getResource("login.fxml") == null) {
            throw new Exception("!No se encontro el archivo login.fxml en com/chat/client/!");
        }

        Scene scene = new Scene(fxmlLoader.load(), 400, 300);


        if (getClass().getResource("styles.css") != null) {
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        }

        stage.setTitle("JavaChat - Acceso");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}