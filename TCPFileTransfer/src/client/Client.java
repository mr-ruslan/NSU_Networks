package client;

import protocol.Request;
import protocol.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Client {

    public static void main(String[] args) {
        Socket socket = new Socket();
        if (args.length < 3) {
            System.out.println("add fileName, address, port");
            return;
        }
        String filePath = args[0];
        String serverName = args[1];
        int serverPort = Integer.parseInt(args[2]);



            Path file = Paths.get(filePath);
        if (!Files.exists(file)){
            System.out.println("file not found");
            return;
        }
        try {
            socket.connect(new InetSocketAddress(serverName, serverPort));
            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();
        Request request = null;

            request = new Request.Upload(filePath, Files.size(file), calculateHash(file), 0);

            request.writeTo(outputStream);

            Response response = Response.readFrom(inputStream);
            if (response instanceof Response.Accept) {
                uploadFile(outputStream, Files.newInputStream(file));
                response = Response.readFrom(inputStream);
                if (response instanceof Response.Success) {
                    System.out.println("Success. File " + ((Response.Success) response).getFileName() + " uploaded");
                } else if (response instanceof Response.Failure) {
                    System.out.println("Failure. File was not uploaded");
                }
            } else if (response instanceof Response.Reject) {
                System.out.println("Server rejected: " + ((Response.Reject) response).error);
            }
            socket.close();

        }catch (IOException e){
            System.out.println("Server closed connection");
        } catch (NoSuchAlgorithmException e) {
        }


    }

    private static byte[] calculateHash(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[4096];
        try (InputStream is = Files.newInputStream(file);
             DigestInputStream dis = new DigestInputStream(is, md)) {
            while (dis.read(buffer) > 0);
        }
        return md.digest();
    }


    private static void uploadFile(OutputStream outputStream, InputStream fileStream) throws IOException {
        byte[] buff = new byte[4096];
        int readBytes;
        while ((readBytes = fileStream.read(buff)) > 0) {
            outputStream.write(buff, 0, readBytes);
        }
    }

}
