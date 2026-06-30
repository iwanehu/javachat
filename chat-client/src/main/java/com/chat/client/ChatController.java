package com.chat.client;

import com.chat.client.network.ChatWebSocketClient; // Asegúrate de importar tu clase de red
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class ChatController {

    @FXML private ListView<String> listaMensajes;
    @FXML private ListView<String> listaUsuarios;
    @FXML private TextField inputMensaje;
    @FXML private Button botonEnviar;

    private ChatWebSocketClient clientWS; // Usamos tu cliente personalizado en lugar del WebSocket nativo suelto
    private String miNombre;

    @FXML
    public void initialize() {
        botonEnviar.setDisable(true);


        // 1. FACTORÍA DE MENSAJES (Burbujas compactas y alineación dinámica)
        listaMensajes.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    javafx.scene.layout.HBox filaContenedora = new javafx.scene.layout.HBox();
                    filaContenedora.getStyleClass().add("message-bubble-row");

                    javafx.scene.control.Label etiquetaTexto = new javafx.scene.control.Label(item);
                    etiquetaTexto.setWrapText(true);
                    etiquetaTexto.getStyleClass().add("chat-bubble");

                    // --- TRUCO JAVAFX PARA PASAR DE BLOQUE COMPLETO A BURBUJA COMPACTA ---
                    // Le indicamos que el ancho máximo sea igual al tamaño que prefiere el texto
                    etiquetaTexto.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                    // ---------------------------------------------------------------------

                    if (item.contains("se ha unido al chat.")) {
                        // Mensajes de sistema
                        etiquetaTexto.getStyleClass().add("bubble-sistema");
                        filaContenedora.setAlignment(javafx.geometry.Pos.CENTER);
                    } else if (item.startsWith(miNombre + ":")) {
                        // TUS PROPIOS MENSAJES: Se alinean a la derecha de la pantalla
                        etiquetaTexto.getStyleClass().add("bubble-propio");
                        filaContenedora.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                    } else {
                        // MENSAJES DE OTROS USUARIOS: Se alinean a la izquierda
                        etiquetaTexto.getStyleClass().add("bubble-usuario");
                        filaContenedora.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    }

                    filaContenedora.getChildren().add(etiquetaTexto);
                    setGraphic(filaContenedora);
                }
            }
        });

        // 2. FACTORÍA DE USUARIOS
        listaUsuarios.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            private final javafx.scene.shape.Circle indicator = new javafx.scene.shape.Circle(4);
            private final javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(8, indicator, new javafx.scene.control.Label());
            { box.setAlignment(javafx.geometry.Pos.CENTER_LEFT); }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String nombreUsuario = item.replace(". ", "").trim();
                    javafx.scene.control.Label lbl = (javafx.scene.control.Label) box.getChildren().get(1);
                    lbl.setText(nombreUsuario);
                    lbl.setStyle("-fx-text-fill: #c0caf5; -fx-font-size: 13px;");
                    indicator.setStyle("-fx-fill: #9ece6a;");
                    setGraphic(box);
                }
            }
        });
    }

    public void setUsuarioLogueado(String usuario) {
        this.miNombre = usuario;

        // Ejecutamos la conexión en un hilo de fondo independiente para dejar libre la UI de JavaFX
        new Thread(this::conectarWebSocket).start();
    }

    private void conectarWebSocket() {
        // Instanciamos  ChatWebSocketClient pasándole la expresión lambda de procesamiento de mensajes
        clientWS = new ChatWebSocketClient(mensaje -> {
            if (mensaje.startsWith("/lista")) {
                String[] usuarios = mensaje.substring(7).split(",");
                Platform.runLater(() -> {
                    listaUsuarios.getItems().clear();
                    for (String u : usuarios) {
                        listaUsuarios.getItems().add(". " + u);
                    }
                });
            } else {
                Platform.runLater(() -> {
                    String mensajeLimpio = mensaje;

                    // Parseo manual rápido para extraer las propiedades "remitente" y "contenido"
                    if (mensaje.contains("\"remitente\"") && mensaje.contains("\"contenido\"")) {
                        try {
                            String remitente = mensaje.split("\"remitente\":\"")[1].split("\"")[0];
                            String contenido = mensaje.split("\"contenido\":\"")[1].split("\"")[0];

                            if ("SISTEMA".equals(remitente)) {
                                mensajeLimpio = contenido; // Se muestra directamente: "prueba4 se ha unido al chat."
                            } else {
                                mensajeLimpio = remitente + ": " + contenido; // Se muestra: "prueba4: hola"
                            }
                        } catch (Exception e) {
                            // En caso de un formato inesperado, se deja el mensaje original como salvaguarda
                            System.err.println("No se pudo parsear el JSON del mensaje: " + e.getMessage());
                        }
                    }

                    listaMensajes.getItems().add(mensajeLimpio);
                });
            }
        });

        try {
            System.out.println("Iniciando conexión WebSocket asíncrona...");
            clientWS.conectar();

            // Acciones en el hilo de la interfaz una vez solicitada la conexión
            Platform.runLater(() -> {
                botonEnviar.setDisable(false);

                // Notificar al servidor que nos hemos unido (usando el formato exacto del backend)
                String jsonUnion = String.format("{\"remitente\":\"SISTEMA\",\"contenido\":\"%s se ha unido al chat.\"}", miNombre);
                clientWS.enviarMensaje(jsonUnion);
            });

        } catch (Exception e) {
            System.err.println("Error crítico al inicializar el cliente WS: " + e.getMessage());
            Platform.runLater(() -> botonEnviar.setDisable(true));
        }
    }

    @FXML
    private void enviarMensaje() {
        String texto = inputMensaje.getText().trim();
        if (!texto.isEmpty() && clientWS != null) {
            String jsonMensaje = String.format("{\"remitente\":\"%s\",\"contenido\":\"%s\"}", miNombre, texto);
            clientWS.enviarMensaje(jsonMensaje);
            inputMensaje.clear();
        }
    }
}