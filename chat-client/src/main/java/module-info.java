module com.chat.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;

   //permite que Javafx acceda a las clases como LoginController
    exports com.chat.client;


    opens com.chat.client to javafx.fxml;
}