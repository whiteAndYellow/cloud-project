package application;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Handler implements Runnable {

    private static int counter = 0;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataInputStream is;
    private final DataOutputStream os;
    private final String name;
    private boolean isRunning;
    private final int BUFFER_SIZE = 8192;
    private final byte[] BUFFER_FILE = new byte[BUFFER_SIZE];

    public Handler(Socket socket) throws IOException {
        is = new DataInputStream(socket.getInputStream());
        os = new DataOutputStream(socket.getOutputStream());
        counter++;
        name = "User#" + counter;
        log.debug("Set nick: {} for new client", name);
        createUserDirIfNotExists();
        isRunning = true;
        sendFileList();
    }

    //flags - 0: textMessage, 1 - send file list
    private void sendFlag(char c) throws IOException {
        os.writeChar(c);
    }

    private String getDate() {
        return formatter.format(LocalDateTime.now());
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    private void saveFile(){
        try {
            //запрос имени файла и создание директории пользователя, если его нет
            String fileName = is.readUTF();
            long fileParts = is.readLong();
            Path userDir = Paths.get(name);
            int bytesReaded = 0;
            Path filePath = Paths.get(userDir.toString(), fileName);

            //создание и запись в файл, перезаписывает его, если тот существует.
            OutputStream fileWriter = Files.newOutputStream(filePath, CREATE, WRITE);
            for (int i = 0; i < fileParts; i++) {
                bytesReaded = is.read(BUFFER_FILE);
                fileWriter.write(BUFFER_FILE,0,bytesReaded);
            }

            fileWriter.close();
            sendFileList();
            log.debug("file {} received",fileName);

        } catch (Exception e) {
            log.error("",e);
        }

    }

    //отправляет пользователю информацию о файлах в его директории
    private void sendFileList() throws IOException {
        sendFlag((char) 1);
        Path userDir = Paths.get(name);
        List<String> fileList = Files.list(userDir).map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
        os.writeInt(fileList.size()); //send files count
        for (String fileName : fileList) {
            os.writeUTF(fileName);
        }
        os.flush();
    }

    private void createUserDirIfNotExists(){
        Path userDir = Paths.get(name);
        if(Files.notExists(userDir)){
            try {
                Files.createDirectory(userDir);
            } catch (IOException e) {
                log.error("userdir ", e);
            }
        }
    }

    private void echo(){
        try {
            String msg = is.readUTF();
            log.debug("received: {}", msg);
            String response = String.format("%s %s: %s", getDate(), name, msg);
            log.debug("Message for response: {}", response);
            sendFlag((char) 0);
            os.writeUTF(response);
            os.flush();
        } catch (IOException e) {
            log.error("",e);
        }
    }


    @Override
    public void run() {
        try {
            while (isRunning) {
                char flag = is.readChar(); // wait data
                if (flag==0) {
                    echo();
                } else {
                    saveFile();
                }
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }


}
