package com.chat.client.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class ChatWebSocketClient {

    private WebSocket webSocket;
    private final Consumer<String> onMessageReceived;


    public ChatWebSocketClient(Consumer<String> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
    }


    public void conectar() {
        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:8080/chat"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        // Cuando llega un mensaje del servidor, se lo pasamos al controlador de la UI
                        onMessageReceived.accept(data.toString());
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.err.println("Error en el WebSocket: " + error.getMessage());
                    }
                })
                .thenAccept(ws -> this.webSocket = ws);
    }

    public void enviarMensaje(String jsonMensaje) {
        if (webSocket != null) {
            webSocket.sendText(jsonMensaje, true);
        }
    }
}
