package application;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainController implements Initializable {

    private Path clientDir;
    public Label textLabel;
    public ListView<String> clientView;
    public ListView<String> serverView;
    public TextField input;
    private DataInputStream is;
    private DataOutputStream os;
    private final int BUFFER_SIZE = 8192;
    private final byte[] BUFFER_FILE = new byte[BUFFER_SIZE];

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            clientDir = Paths.get("cloud-project-client", "client");
            if (!Files.exists(clientDir)) {
                Files.createDirectory(clientDir);
            }
            clientView.getItems().clear();
            clientView.getItems().addAll(getFiles(clientDir));
            Socket socket = new Socket("localhost", 8189);
            is = new DataInputStream(socket.getInputStream());
            os = new DataOutputStream(socket.getOutputStream());
            Thread readThread = new Thread(this::read);
            readThread.setDaemon(true);
            readThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> getFiles(Path path) throws IOException {
        return Files.list(path).map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
    }

    private void read() {
        try {
            while (true) {
                char flag = is.readChar();
                if (flag == 0) {
                    receiveServerTextMessage();
                } else {
                    updateServerViewFiles();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void receiveServerTextMessage() {
        try {
            String msg = is.readUTF();
            Platform.runLater(() -> textLabel.setText(msg));
        } catch (Exception e) {
            log.error("", e);
        }
    }

    public void sendMessage(ActionEvent actionEvent) throws IOException {
        String text = input.getText();
        sendFlag((char) 0);
        os.writeUTF(text);
        os.flush();
        input.clear();
    }


    public void sendFile(ActionEvent actionEvent) throws IOException {
        String fileName = clientView.getSelectionModel().getSelectedItem();
        Path filePath = Paths.get(clientDir.toString(), fileName);
        sendFlag((char) 1);
        os.writeUTF(fileName); //sending the filename
        os.writeLong(Files.size(filePath)/BUFFER_SIZE+1); // sending the number of parts of the file
        //sending file
        int sendBytes = 0;
        InputStream fileSender = Files.newInputStream(filePath);
        while ((sendBytes = fileSender.read(BUFFER_FILE)) > 0) {
            os.write(BUFFER_FILE,0,sendBytes);
        }
        fileSender.close();
        os.flush();
    }

    private void updateServerViewFiles() {
        try {
            int filesCount = is.readInt();
            Platform.runLater(() -> serverView.getItems().clear());
            for (int i = 0; i < filesCount; i++) {
                String fileName = is.readUTF();
                Platform.runLater(() -> serverView.getItems().add(fileName));
            }
            log.debug("updated: {} files", filesCount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //flags - 0: textMessage, 1 - send file
    private void sendFlag(char c) throws IOException {
        os.writeChar(c);
    }
}
