package com.chat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import static javafx.application.Application.launch;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Carga el FXML desde la ruta de resources
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chat/client/chat-view.fxml"));
        Parent root = loader.load();

        primaryStage.setTitle("JavaFX Chat - Nivel 0");
        primaryStage.setScene(new Scene(root, 400, 500));

        // Nos aseguramos de cerrar el sistema al cerrar la ventana
        primaryStage.setOnCloseRequest(event -> System.exit(0));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
